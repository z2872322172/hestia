package com.housekeeping.platform.scheduling.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.scheduling.application.command.BatchScheduleCommand;
import com.housekeeping.platform.scheduling.application.view.ScheduleView;
import com.housekeeping.platform.scheduling.domain.ScheduleStatus;
import com.housekeeping.platform.scheduling.domain.ScheduleType;
import com.housekeeping.platform.scheduling.domain.SlotStatus;
import com.housekeeping.platform.scheduling.persistence.entity.ServiceWindowEntity;
import com.housekeeping.platform.scheduling.persistence.entity.WorkerScheduleEntity;
import com.housekeeping.platform.scheduling.persistence.entity.WorkerSlotEntity;
import com.housekeeping.platform.scheduling.persistence.mapper.WorkerScheduleMapper;
import com.housekeeping.platform.scheduling.persistence.mapper.WorkerSlotMapper;
import com.housekeeping.platform.workforce.application.WorkerProfileService;
import com.housekeeping.platform.workforce.domain.AuditStatus;
import com.housekeeping.platform.workforce.domain.WorkStatus;
import com.housekeeping.platform.workforce.persistence.entity.WorkerProfileEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员排班服务：批量生成排班、请假、停排，以及排班到时段的生成。
 *
 * <p>核心不变量：</p>
 * <ul>
 *   <li>一条 {@code WORK + ACTIVE} 排班对应一个 AVAILABLE 时段；
 *       请假/培训/休息排班对应 BLOCKED 时段（docs/modules/06 第 6.1 节）；</li>
 *   <li>同一人员同日同窗只有一条排班——应用层先查只为提示友好，
 *       并发兜底是 {@code uk_worker_schedule} 唯一索引；</li>
 *   <li>排班变更（请假/停排/取消）遇到 HELD/BOOKED 时段一律拒绝并抛
 *       {@code SCHEDULE_CHANGE_HAS_BOOKED_ORDER}——已占用时段只能走改派或改期，
 *       绝不能被排班变更静默吞掉（docs/modules/06 第 12.2 节）。</li>
 * </ul>
 */
@Service
@RequiresPersistence
public class WorkerScheduleService {

    private final WorkerScheduleMapper scheduleMapper;

    private final WorkerSlotMapper slotMapper;

    private final ServiceWindowService windowService;

    private final WorkerProfileService workerProfileService;

    public WorkerScheduleService(WorkerScheduleMapper scheduleMapper,
                                 WorkerSlotMapper slotMapper,
                                 ServiceWindowService windowService,
                                 WorkerProfileService workerProfileService) {
        this.scheduleMapper = scheduleMapper;
        this.slotMapper = slotMapper;
        this.windowService = windowService;
        this.workerProfileService = workerProfileService;
    }

    /**
     * 批量创建排班并生成时段。
     *
     * <p>整批一个事务：任何一个日期 × 窗口组合冲突，整批回滚，
     * 避免出现"排了一半"的中间态。</p>
     *
     * @param command        批量命令（运营端带 {@code workerNo}，自助端为 {@code null} 表示本人）
     * @param operatorAccountId 运营账号 ID（自助场景传本人账号 ID）
     * @param selfService    是否人员自助（自助只允许给自己排 WORK，且日期必须在明天及以后）
     * @return 生成的排班视图
     */
    @Transactional
    public List<ScheduleView> batchCreate(BatchScheduleCommand command, long operatorAccountId,
                                          boolean selfService) {
        WorkerProfileEntity worker = resolveWorker(command, operatorAccountId, selfService);
        List<LocalDate> dates = command.serviceDates();
        List<Long> windowIds = command.windowIds();
        if (dates == null || dates.isEmpty() || windowIds == null || windowIds.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        ScheduleType type = ScheduleType.of(command.scheduleType());
        if (selfService && type != ScheduleType.WORK) {
            // 自助排班只允许报工作；请假是另一个入口，避免"自助请假"绕过运营对休息时段的可见性
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        LocalDate today = LocalDate.now();
        for (LocalDate date : dates) {
            if (selfService ? !date.isAfter(today) : date.isBefore(today)) {
                // 自助端要求至少提前一天；运营端允许排当天
                throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
            }
        }
        List<ServiceWindowEntity> windows = windowIds.stream()
                .map(windowService::requireActiveById).toList();
        List<ScheduleView> created = new ArrayList<>(dates.size() * windowIds.size());
        for (LocalDate date : dates) {
            for (ServiceWindowEntity window : windows) {
                created.add(createOne(worker, date, window, type, command.note(),
                        selfService ? "WORKER" : "OPERATOR", operatorAccountId));
            }
        }
        return created;
    }

    /**
     * 提交请假：把指定日期时间窗的排班改为请假类型。
     *
     * <p>对应 docs/modules/06 第 12 节：无占用的时段转 BLOCKED；
     * 有 HELD/BOOKED 占用的时段拒绝，必须走改派流程。</p>
     *
     * @param scheduleId        排班 ID
     * @param operatorAccountId 操作账号
     * @param selfService       是否本人操作
     * @return 更新后的排班视图
     */
    @Transactional
    public ScheduleView markLeave(long scheduleId, long operatorAccountId, boolean selfService) {
        WorkerScheduleEntity schedule = requireSchedule(scheduleId);
        assertOwnership(schedule, operatorAccountId, selfService);
        assertScheduleActive(schedule);
        WorkerSlotEntity slot = requireSlotBySchedule(scheduleId);
        blockIfFree(slot);
        schedule.setScheduleType(ScheduleType.LEAVE.name());
        schedule.setVersion(schedule.getVersion());
        if (scheduleMapper.updateById(schedule) == 0) {
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
        }
        return toView(schedule, slot);
    }

    /**
     * 停排/取消排班：排班置 CANCELED，空闲时段按日期转 BLOCKED 或 CLOSED。
     *
     * @param scheduleId        排班 ID
     * @param operatorAccountId 操作账号
     * @param selfService       是否本人操作
     * @return 更新后的排班视图
     */
    @Transactional
    public ScheduleView cancel(long scheduleId, long operatorAccountId, boolean selfService) {
        WorkerScheduleEntity schedule = requireSchedule(scheduleId);
        assertOwnership(schedule, operatorAccountId, selfService);
        assertScheduleActive(schedule);
        WorkerSlotEntity slot = requireSlotBySchedule(scheduleId);
        if (SlotStatus.of(slot.getStatus()).occupiedByOrder()) {
            throw new BusinessException(ErrorCode.SCHEDULE_CHANGE_HAS_BOOKED_ORDER);
        }
        if (SlotStatus.of(slot.getStatus()) == SlotStatus.AVAILABLE) {
            if (schedule.getServiceDate().isBefore(LocalDate.now())) {
                slotMapper.closeAvailable(slot.getId());
            } else {
                slotMapper.blockAvailable(slot.getId());
            }
        }
        schedule.setStatus(ScheduleStatus.CANCELED.name());
        schedule.setVersion(schedule.getVersion());
        if (scheduleMapper.updateById(schedule) == 0) {
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
        }
        return toView(schedule, slotMapper.selectById(slot.getId()));
    }

    /**
     * 人员自助提交请假：在指定日期时间窗生成一条 LEAVE 排班（时段为 BLOCKED）。
     *
     * <p>若该日期时间窗已有排班（例如已排 WORK），唯一约束会拒绝——
     * 人员必须先取消工作排班再请假，不允许同一天同一窗口既工作又请假。</p>
     *
     * @param accountId  当前登录账号 ID
     * @param serviceDate 请假日期（必须晚于今天）
     * @param windowId   时间窗 ID
     * @param note       请假原因
     * @return 请假排班视图
     */
    @Transactional
    public ScheduleView submitMyLeave(long accountId, LocalDate serviceDate, long windowId,
                                      String note) {
        WorkerProfileEntity worker = workerProfileService.requireByAccountId(accountId);
        if (WorkStatus.valueOf(worker.getWorkStatus()) != WorkStatus.ACTIVE
                || AuditStatus.valueOf(worker.getAuditStatus()) != AuditStatus.APPROVED) {
            throw new BusinessException(ErrorCode.WORKER_NOT_ACTIVE);
        }
        if (!serviceDate.isAfter(LocalDate.now())) {
            // 请假至少提前一天，当天临时请假走线下与运营调整（docs/modules/06 第 4.2 节允许时间内维护）
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
        }
        ServiceWindowEntity window = windowService.requireActiveById(windowId);
        return createOne(worker, serviceDate, window, ScheduleType.LEAVE, note, "WORKER", accountId);
    }

    /**
     * 查询本人的排班（自助端入口）。
     *
     * @param accountId 当前登录账号 ID
     * @param dateFrom  起始日期（含）
     * @param dateTo    结束日期（含）
     * @return 排班视图列表
     */
    @Transactional(readOnly = true)
    public List<ScheduleView> listMy(long accountId, LocalDate dateFrom, LocalDate dateTo) {
        return listByWorker(workerProfileService.requireByAccountId(accountId).getId(),
                dateFrom, dateTo);
    }

    /**
     * 查询人员在日期区间内的排班（本人与运营共用）。
     *
     * @param workerId 人员 ID
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return 排班视图列表
     */
    @Transactional(readOnly = true)
    public List<ScheduleView> listByWorker(long workerId, LocalDate dateFrom, LocalDate dateTo) {
        assertQueryRange(dateFrom, dateTo);
        List<WorkerScheduleEntity> schedules =
                scheduleMapper.selectByWorkerAndDateRange(workerId, dateFrom, dateTo);
        return assembleViews(schedules);
    }

    /**
     * 运营视角：日期区间内的全部排班。
     *
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return 排班视图列表
     */
    @Transactional(readOnly = true)
    public List<ScheduleView> listByDateRange(LocalDate dateFrom, LocalDate dateTo) {
        assertQueryRange(dateFrom, dateTo);
        return assembleViews(scheduleMapper.selectByDateRange(dateFrom, dateTo));
    }

    /**
     * 修改排班：改日期或时间窗，仅空闲时段允许。
     *
     * <p>已锁定（HELD）与已占用（BOOKED）时段一律拒绝——锁定期间移动排班
     * 会让持锁订单指向一个已经不存在的时段。日期与时间窗可缺省，
     * 缺省表示保持不变；仅改备注不算"移动"，不做唯一性冲突检查。</p>
     *
     * @param scheduleId        排班 ID
     * @param serviceDate       新的服务日期，可空
     * @param windowId          新的时间窗 ID，可空
     * @param note              备注，可空表示不修改
     * @param operatorAccountId 操作账号
     * @return 更新后的排班视图
     */
    @Transactional
    public ScheduleView update(long scheduleId, LocalDate serviceDate, Long windowId, String note,
                               long operatorAccountId) {
        WorkerScheduleEntity schedule = requireSchedule(scheduleId);
        assertScheduleActive(schedule);
        WorkerSlotEntity slot = requireSlotBySchedule(scheduleId);
        if (SlotStatus.of(slot.getStatus()) != SlotStatus.AVAILABLE) {
            throw new BusinessException(ErrorCode.SCHEDULE_CHANGE_HAS_BOOKED_ORDER);
        }
        LocalDate targetDate = serviceDate == null ? schedule.getServiceDate() : serviceDate;
        Long targetWindowId = windowId == null ? schedule.getWindowId() : windowId;
        if (targetDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
        }
        ServiceWindowEntity window = windowService.requireActiveById(targetWindowId);
        boolean moved = !targetDate.equals(schedule.getServiceDate())
                || !window.getId().equals(schedule.getWindowId());
        if (moved) {
            Long existing = scheduleMapper.selectCount(new LambdaQueryWrapper<WorkerScheduleEntity>()
                    .eq(WorkerScheduleEntity::getWorkerId, schedule.getWorkerId())
                    .eq(WorkerScheduleEntity::getServiceDate, targetDate)
                    .eq(WorkerScheduleEntity::getWindowId, window.getId())
                    .ne(WorkerScheduleEntity::getId, schedule.getId()));
            if (existing != null && existing > 0) {
                throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
            }
        }
        schedule.setServiceDate(targetDate);
        schedule.setWindowId(window.getId());
        if (note != null) {
            schedule.setNote(note);
        }
        schedule.setVersion(schedule.getVersion());
        if (scheduleMapper.updateById(schedule) == 0) {
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
        }
        if (moved) {
            slot.setServiceDate(targetDate);
            slot.setWindowId(window.getId());
            slot.setVersion(slot.getVersion());
            if (slotMapper.updateById(slot) == 0) {
                throw new BusinessException(ErrorCode.WORKER_SLOT_NOT_AVAILABLE);
            }
        }
        return toView(schedule, slotMapper.selectById(slot.getId()));
    }

    /**
     * 按人员编号取排班（运营指定人员查询）。
     *
     * @param workerNo 人员编号
     * @param dateFrom 起始日期（含）
     * @param dateTo   结束日期（含）
     * @return 排班视图列表
     */
    @Transactional(readOnly = true)
    public List<ScheduleView> listByWorkerNo(String workerNo, LocalDate dateFrom, LocalDate dateTo) {
        return listByWorker(workerProfileService.requireByWorkerNo(workerNo).getId(), dateFrom, dateTo);
    }

    /**
     * 创建单条排班与时段。
     */
    private ScheduleView createOne(WorkerProfileEntity worker, LocalDate date,
                                   ServiceWindowEntity window, ScheduleType type,
                                   String note, String source, long operatorAccountId) {
        Long existing = scheduleMapper.selectCount(new LambdaQueryWrapper<WorkerScheduleEntity>()
                .eq(WorkerScheduleEntity::getWorkerId, worker.getId())
                .eq(WorkerScheduleEntity::getServiceDate, date)
                .eq(WorkerScheduleEntity::getWindowId, window.getId()));
        if (existing != null && existing > 0) {
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
        }
        WorkerScheduleEntity schedule = new WorkerScheduleEntity();
        schedule.setWorkerId(worker.getId());
        schedule.setServiceDate(date);
        schedule.setWindowId(window.getId());
        schedule.setScheduleType(type.name());
        schedule.setStatus(ScheduleStatus.ACTIVE.name());
        schedule.setSource(source);
        schedule.setNote(note);
        schedule.setCreatedByAccountId(operatorAccountId);
        schedule.setUpdatedByAccountId(operatorAccountId);
        scheduleMapper.insert(schedule);

        WorkerSlotEntity slot = new WorkerSlotEntity();
        slot.setWorkerId(worker.getId());
        slot.setScheduleId(schedule.getId());
        slot.setServiceDate(date);
        slot.setWindowId(window.getId());
        slot.setStatus(type.generatesBookableSlot()
                ? SlotStatus.AVAILABLE.name() : SlotStatus.BLOCKED.name());
        slot.setCreatedByAccountId(operatorAccountId);
        slot.setUpdatedByAccountId(operatorAccountId);
        slotMapper.insert(slot);
        return toView(schedule, slot);
    }

    /**
     * 排班查询区间校验：区间过宽只会放大数据库扫描，没有业务价值。
     */
    private void assertQueryRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null || dateFrom.isAfter(dateTo)
                || dateFrom.plusDays(31).isBefore(dateTo)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
    }

    /**
     * 解析排班目标人员：运营按编号，自助按当前账号。
     */
    private WorkerProfileEntity resolveWorker(BatchScheduleCommand command, long operatorAccountId,
                                              boolean selfService) {
        WorkerProfileEntity worker = selfService
                ? workerProfileService.requireByAccountId(operatorAccountId)
                : workerProfileService.requireByWorkerNo(command.workerNo());
        // 只有"在岗且审核通过"的人员才能排班；ONBOARDING/LEFT/SUSPENDED 一律拒绝（docs 19.1）
        if (WorkStatus.valueOf(worker.getWorkStatus()) != WorkStatus.ACTIVE
                || AuditStatus.valueOf(worker.getAuditStatus()) != AuditStatus.APPROVED) {
            throw new BusinessException(ErrorCode.WORKER_NOT_ACTIVE);
        }
        return worker;
    }

    private WorkerScheduleEntity requireSchedule(long scheduleId) {
        WorkerScheduleEntity schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_NOT_FOUND);
        }
        return schedule;
    }

    private void assertScheduleActive(WorkerScheduleEntity schedule) {
        if (ScheduleStatus.of(schedule.getStatus()) != ScheduleStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_CONFLICT);
        }
    }

    /**
     * 自助操作只能作用于本人排班；运营操作不限。
     */
    private void assertOwnership(WorkerScheduleEntity schedule, long accountId, boolean selfService) {
        if (!selfService) {
            return;
        }
        WorkerProfileEntity self = workerProfileService.requireByAccountId(accountId);
        if (!self.getId().equals(schedule.getWorkerId())) {
            // 用"不存在"回应越权，避免探测他人排班 ID
            throw new BusinessException(ErrorCode.WORKER_SCHEDULE_NOT_FOUND);
        }
    }

    private WorkerSlotEntity requireSlotBySchedule(long scheduleId) {
        WorkerSlotEntity slot = slotMapper.selectOne(new LambdaQueryWrapper<WorkerSlotEntity>()
                .eq(WorkerSlotEntity::getScheduleId, scheduleId));
        if (slot == null) {
            throw new BusinessException(ErrorCode.WORKER_SLOT_NOT_FOUND);
        }
        return slot;
    }

    /**
     * 空闲时段转阻塞；已占用时段拒绝请假/停排。
     *
     * <p>条件更新成功后必须同步内存对象——调用方还要用这个实体组装响应，
     * 否则客户端看到的时段状态是更新前的旧值。</p>
     */
    private void blockIfFree(WorkerSlotEntity slot) {
        SlotStatus status = SlotStatus.of(slot.getStatus());
        if (status.occupiedByOrder()) {
            throw new BusinessException(ErrorCode.SCHEDULE_CHANGE_HAS_BOOKED_ORDER);
        }
        if (status == SlotStatus.AVAILABLE) {
            if (slotMapper.blockAvailable(slot.getId()) == 0) {
                // 并发下刚好被订单锁走：按"存在已占用时段"处理
                throw new BusinessException(ErrorCode.SCHEDULE_CHANGE_HAS_BOOKED_ORDER);
            }
            slot.setStatus(SlotStatus.BLOCKED.name());
        }
    }

    /**
     * 组装视图：批量补齐时间窗信息与时段状态，避免逐条查询。
     */
    private List<ScheduleView> assembleViews(List<WorkerScheduleEntity> schedules) {
        if (schedules.isEmpty()) {
            return List.of();
        }
        List<Long> windowIds = schedules.stream()
                .map(WorkerScheduleEntity::getWindowId).distinct().toList();
        Map<Long, ServiceWindowEntity> windows = new HashMap<>(windowIds.size());
        for (ServiceWindowEntity window : windowService.listByIds(windowIds)) {
            windows.put(window.getId(), window);
        }
        List<Long> scheduleIds = schedules.stream().map(WorkerScheduleEntity::getId).toList();
        Map<Long, WorkerSlotEntity> slots = new HashMap<>(scheduleIds.size());
        for (WorkerSlotEntity slot : slotMapper.selectList(new LambdaQueryWrapper<WorkerSlotEntity>()
                .in(WorkerSlotEntity::getScheduleId, scheduleIds))) {
            slots.put(slot.getScheduleId(), slot);
        }
        List<ScheduleView> views = new ArrayList<>(schedules.size());
        Map<Long, String> workerNos = new HashMap<>(schedules.size());
        for (WorkerScheduleEntity schedule : schedules) {
            WorkerSlotEntity slot = slots.get(schedule.getId());
            ServiceWindowEntity window = windows.get(schedule.getWindowId());
            views.add(new ScheduleView(schedule.getId(),
                    workerNos.computeIfAbsent(schedule.getWorkerId(), this::resolveWorkerNo),
                    schedule.getServiceDate().toString(),
                    window == null ? null : window.getWindowCode(),
                    window == null ? null : window.getWindowName(),
                    schedule.getScheduleType(), schedule.getStatus(),
                    slot == null ? null : slot.getStatus(), schedule.getNote()));
        }
        return views;
    }

    private String resolveWorkerNo(long workerId) {
        return workerProfileService.requireById(workerId).getWorkerNo();
    }

    private ScheduleView toView(WorkerScheduleEntity schedule, WorkerSlotEntity slot) {
        ServiceWindowEntity window = windowService.requireById(schedule.getWindowId());
        return new ScheduleView(schedule.getId(),
                workerProfileService.requireById(schedule.getWorkerId()).getWorkerNo(),
                schedule.getServiceDate().toString(),
                window.getWindowCode(), window.getWindowName(),
                schedule.getScheduleType(), schedule.getStatus(),
                slot == null ? null : slot.getStatus(), schedule.getNote());
    }
}
