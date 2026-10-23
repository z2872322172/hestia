package com.housekeeping.platform.scheduling.application;

import com.housekeeping.platform.catalog.application.ServiceCatalogQueryService;
import com.housekeeping.platform.catalog.application.view.ServiceItemRef;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.scheduling.application.view.AvailabilityView;
import com.housekeeping.platform.scheduling.application.view.ServiceWindowView;
import com.housekeeping.platform.scheduling.domain.WindowTimeline;
import com.housekeeping.platform.scheduling.persistence.mapper.WorkerSlotMapper;
import com.housekeeping.platform.workforce.application.WorkerEligibilityService;
import com.housekeeping.platform.workforce.application.WorkerProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 可预约性聚合查询（docs/modules/06 第 8 节）。
 *
 * <p>消费者看到的是"某天某时间窗是否至少存在一名合格人员"，
 * 具体是谁、还剩几个时段都不暴露。过滤链：</p>
 *
 * <ol>
 *   <li>服务项目必须已上架（未上架与"不存在"表现一致，避免用错误码探测未发布商品）；</li>
 *   <li>服务时长必须能放进时间窗——放不进的窗口直接不可约，而不是报错；</li>
 *   <li>时段库存有 AVAILABLE（scheduling 自己的表）；</li>
 *   <li>人员当日 BOOKED 数未超过接单上限（每日上限属于人员档案）；</li>
 *   <li>技能/资质/账号/区域等人员资格（workforce 的 {@code WorkerEligibilityService}）。</li>
 * </ol>
 */
@Service
@RequiresPersistence
public class AvailabilityQueryService {

    /** 查询区间上限：可用性是"给消费者看未来"的查询，范围过大只会拖垮数据库。 */
    private static final int MAX_QUERY_DAYS = 14;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ServiceCatalogQueryService catalogQueryService;

    private final ServiceWindowService windowService;

    private final WorkerSlotMapper slotMapper;

    private final WorkerEligibilityService workerEligibilityService;

    private final WorkerProfileService workerProfileService;

    public AvailabilityQueryService(ServiceCatalogQueryService catalogQueryService,
                                    ServiceWindowService windowService,
                                    WorkerSlotMapper slotMapper,
                                    WorkerEligibilityService workerEligibilityService,
                                    WorkerProfileService workerProfileService) {
        this.catalogQueryService = catalogQueryService;
        this.windowService = windowService;
        this.slotMapper = slotMapper;
        this.workerEligibilityService = workerEligibilityService;
        this.workerProfileService = workerProfileService;
    }

    /**
     * 查询日期区间内各时间窗的聚合可用性。
     *
     * @param serviceItemNo 服务项目编号
     * @param serviceAreaId 服务区域 ID
     * @param dateFrom      起始日期（含）
     * @param dateTo        结束日期（含），区间最长 14 天
     * @return 按日期升序的可用性视图
     * @throws BusinessException 服务项目不存在或区间非法
     */
    @Transactional(readOnly = true)
    public List<AvailabilityView> query(String serviceItemNo, Long serviceAreaId,
                                        LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null || dateFrom.isAfter(dateTo)
                || dateFrom.isBefore(LocalDate.now())
                || dateFrom.plusDays(MAX_QUERY_DAYS - 1).isBefore(dateTo)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        ServiceItemRef item = catalogQueryService.requireItemRef(serviceItemNo);
        if (!item.published()) {
            // 与公开目录一致：未上架项目看起来"不存在"
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<ServiceWindowView> activeWindows = windowService.listActive().stream()
                .filter(window -> WindowTimeline.fits(item.durationMinutes(),
                        window.startTime(), window.endTime()))
                .toList();
        if (activeWindows.isEmpty()) {
            return List.of();
        }
        List<AvailabilityView> result = new ArrayList<>();
        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            result.add(assessDate(date, serviceItemNo, serviceAreaId, activeWindows));
        }
        return result;
    }

    /**
     * 评估单日各时间窗的可用性。
     */
    private AvailabilityView assessDate(LocalDate date, String serviceItemNo, Long serviceAreaId,
                                        List<ServiceWindowView> windows) {
        List<AvailabilityView.WindowAvailability> windowViews = new ArrayList<>(windows.size());
        for (ServiceWindowView window : windows) {
            windowViews.add(assessWindow(date, window, serviceItemNo, serviceAreaId));
        }
        return new AvailabilityView(DATE_FORMAT.format(date), windowViews);
    }

    /**
     * 返回某日某时间窗下全部合格人员 ID（booking 下单选人使用，docs/modules/07 第 7 节）。
     *
     * <p>与 {@link #query} 共用同一条过滤链（时段库存 → 每日接单上限 → 人员资格），
     * 区别是返回人员集合而不是聚合布尔值；顺序即派单候选默认名次。
     * 没有任何合格人员时抛 {@code SLOT_NOT_AVAILABLE}——下单流程直接把它当作
     * "该时段已不可预约"的对外语义。</p>
     *
     * @param serviceItemNo 服务项目编号
     * @param serviceAreaId 服务区域 ID
     * @param serviceDate   服务日期
     * @param windowId      时间窗 ID
     * @return 有序合格人员 ID 列表
     * @throws BusinessException 无合格人员或服务项目不可约
     */
    @Transactional(readOnly = true)
    public List<Long> availableWorkerIds(String serviceItemNo, Long serviceAreaId,
                                         LocalDate serviceDate, Long windowId) {
        ServiceItemRef item = catalogQueryService.requireItemRef(serviceItemNo);
        if (!item.published()) {
            // 与公开目录一致：未上架项目看起来"不存在"
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        List<Long> candidates = slotMapper.selectAvailableWorkerIds(serviceDate, windowId);
        List<Long> capable = filterCapableWorkers(serviceDate, candidates);
        List<Long> eligible = workerEligibilityService.filterEligible(capable, serviceItemNo, serviceAreaId);
        if (eligible.isEmpty()) {
            throw new BusinessException(ErrorCode.SLOT_NOT_AVAILABLE);
        }
        return eligible;
    }

    /**
     * 评估单个时间窗：库存 → 接单上限 → 人员资格，任一层为空即不可约。
     */
    private AvailabilityView.WindowAvailability assessWindow(LocalDate date,
                                                             ServiceWindowView window,
                                                             String serviceItemNo,
                                                             Long serviceAreaId) {
        List<Long> candidates = slotMapper.selectAvailableWorkerIds(date, window.windowId());
        List<Long> capable = filterCapableWorkers(date, candidates);
        boolean available = !capable.isEmpty()
                && workerEligibilityService.anyEligible(capable, serviceItemNo, serviceAreaId);
        return new AvailabilityView.WindowAvailability(window.windowCode(), window.windowName(),
                window.startTime().toString(), window.endTime().toString(), available);
    }

    /**
     * 接单上限过滤（时段库存候选 → 未超每日上限的子集）。
     */
    private List<Long> filterCapableWorkers(LocalDate date, List<Long> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<Long, Integer> bookedCounts = new HashMap<>(candidates.size());
        for (Map<String, Object> row : slotMapper.countBookedGroupByWorker(date)) {
            Object workerId = row.get("workerId");
            Object count = row.get("bookedCount");
            if (workerId instanceof Number workerIdValue && count instanceof Number countValue) {
                bookedCounts.put(workerIdValue.longValue(), countValue.intValue());
            }
        }
        Map<Long, Integer> caps = workerProfileService.maxDailyOrdersByIds(candidates);
        return candidates.stream()
                .filter(workerId -> bookedCounts.getOrDefault(workerId, 0)
                        < caps.getOrDefault(workerId, 0))
                .toList();
    }
}
