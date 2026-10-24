package com.housekeeping.platform.fulfillment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.booking.application.BookingStatusCommandService;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.catalog.application.view.ServiceItemSnapshot;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.CustomerProfileService;
import com.housekeeping.platform.fulfillment.application.view.FulfillmentView;
import com.housekeeping.platform.fulfillment.domain.FulfillmentStatus;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceChecklistItemEntity;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceEvidenceEntity;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceExceptionEntity;
import com.housekeeping.platform.fulfillment.persistence.entity.ServiceRecordEntity;
import com.housekeeping.platform.fulfillment.persistence.mapper.ServiceChecklistItemMapper;
import com.housekeeping.platform.fulfillment.persistence.mapper.ServiceEvidenceMapper;
import com.housekeeping.platform.fulfillment.persistence.mapper.ServiceExceptionMapper;
import com.housekeeping.platform.fulfillment.persistence.mapper.ServiceRecordMapper;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 履约应用服务（docs/modules/09）。
 *
 * <p>链路：接单建记录（复制服务标准快照为 checklist）→ 出发 → 签到 →
 * 开始服务 → 逐项打勾/传证据 → 异常上报/处理 → 申请完成 → 客户确认或自动确认。</p>
 *
 * <p>铁律（docs 第 2 节）：只有当前分配人员可推进履约（改派后旧人员被
 * worker_id 校验天然挡住）；必填检查项与证据不齐不能申请完成；服务人员不能
 * 代替客户确认；模块只通过订单命令推进主状态，不直接写订单表。</p>
 *
 * <p>状态流转一律条件更新，影响行数 0 抛 {@code FULFILLMENT_STATUS_CONFLICT}；
 * 客户确认与自动确认并发时条件更新只有一个成功。</p>
 */
@Service
@RequiresPersistence
public class FulfillmentApplicationService {

    private static final String SEQ_RECORD = "RECORD";

    private static final String SEQ_EXCEPTION = "FEX";

    private static final String PREFIX_RECORD = "SRV";

    private static final String PREFIX_EXCEPTION = "FEX";

    /** 签到允许窗口：预约开始前 60 分钟到预约结束后 120 分钟（docs 第 7.2 节范围规则）。 */
    private static final Duration CHECK_IN_BEFORE_MINUTES = Duration.ofMinutes(60);

    private static final Duration CHECK_IN_AFTER_MINUTES = Duration.ofMinutes(120);

    /** 客户自动确认时限：申请完成后 24 小时（docs 第 11.3 节，系统按规则自动确认）。 */
    private static final Duration AUTO_CONFIRM_HOURS = Duration.ofHours(24);

    private final ServiceRecordMapper recordMapper;

    private final ServiceChecklistItemMapper checklistMapper;

    private final ServiceEvidenceMapper evidenceMapper;

    private final ServiceExceptionMapper exceptionMapper;

    private final BookingOrderMapper orderMapper;

    private final BookingStatusCommandService bookingStatusCommandService;

    private final CustomerProfileService customerProfileService;

    private final BusinessNoGenerator noGenerator;

    private final ObjectMapper objectMapper;

    public FulfillmentApplicationService(ServiceRecordMapper recordMapper,
                                         ServiceChecklistItemMapper checklistMapper,
                                         ServiceEvidenceMapper evidenceMapper,
                                         ServiceExceptionMapper exceptionMapper,
                                         BookingOrderMapper orderMapper,
                                         BookingStatusCommandService bookingStatusCommandService,
                                         CustomerProfileService customerProfileService,
                                         BusinessNoGenerator noGenerator,
                                         ObjectMapper objectMapper) {
        this.recordMapper = recordMapper;
        this.checklistMapper = checklistMapper;
        this.evidenceMapper = evidenceMapper;
        this.exceptionMapper = exceptionMapper;
        this.orderMapper = orderMapper;
        this.bookingStatusCommandService = bookingStatusCommandService;
        this.customerProfileService = customerProfileService;
        this.noGenerator = noGenerator;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 记录创建（接单驱动，docs/modules/09 第 6 节）
    // ------------------------------------------------------------------

    /**
     * 人员接单后创建履约记录，并把下单时刻的服务标准快照复制为检查项。
     *
     * <p>订单已存在未完成记录（改派场景）时按 docs 第 18.2 节重置换绑新人员，
     * 检查项快照保持不变；已 COMPLETED / CANCELED 的记录不会走到这里
     * （订单侧状态已不允许再次接单）。</p>
     *
     * @param bookingId 订单 ID
     * @param workerId  接单人员 ID
     * @param accountId 发起账号 ID（系统调用可空）
     */
    @Transactional
    public void createForBooking(long bookingId, long workerId, Long accountId) {
        ServiceRecordEntity existing = recordMapper.selectByBookingId(bookingId);
        if (existing != null) {
            recordMapper.resetForReassignment(bookingId, workerId);
            return;
        }
        BookingOrderEntity order = orderMapper.selectById(bookingId);
        if (order == null) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        ServiceRecordEntity record = new ServiceRecordEntity();
        record.setRecordNo(noGenerator.next(SEQ_RECORD, PREFIX_RECORD));
        record.setBookingId(bookingId);
        record.setWorkerId(workerId);
        record.setStatus(FulfillmentStatus.PENDING.name());
        record.setCreatedByAccountId(accountId);
        record.setUpdatedByAccountId(accountId);
        recordMapper.insert(record);
        copyChecklist(record.getId(), order.getServiceStandardSnapshotJson(), accountId);
    }

    /** 从订单服务标准快照 JSON 复制检查项（docs 第 8 节：订单后模板变化不影响）。 */
    private void copyChecklist(long recordId, String snapshotJson, Long accountId) {
        List<ServiceItemSnapshot.StandardSnapshot> standards;
        try {
            ServiceItemSnapshot snapshot =
                    objectMapper.readValue(snapshotJson, ServiceItemSnapshot.class);
            standards = snapshot.serviceStandardItems();
        } catch (Exception exception) {
            throw new IllegalStateException("订单服务标准快照解析失败", exception);
        }
        if (standards == null || standards.isEmpty()) {
            return;
        }
        for (ServiceItemSnapshot.StandardSnapshot standard : standards) {
            ServiceChecklistItemEntity item = new ServiceChecklistItemEntity();
            item.setServiceRecordId(recordId);
            item.setStandardGroup(standard.standardGroup());
            item.setItemName(standard.itemName());
            item.setDescription(standard.description());
            item.setRequired(standard.required());
            item.setEvidenceRequired(standard.evidenceRequired());
            item.setCompleted(false);
            item.setSortOrder(standard.sortOrder());
            item.setCreatedByAccountId(accountId);
            checklistMapper.insert(item);
        }
    }

    // ------------------------------------------------------------------
    // 出发 / 签到 / 开始（docs/modules/09 第 7 节）
    // ------------------------------------------------------------------

    /**
     * 出发：PENDING → EN_ROUTE，订单主状态 CONFIRMED → EN_ROUTE。
     *
     * @param workerId 人员 ID
     * @param orderNo  订单号
     * @return 履约聚合视图
     */
    @Transactional
    public FulfillmentView depart(long workerId, String orderNo) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        requireCurrentWorker(record, workerId);
        if (recordMapper.depart(record.getId(), workerId) == 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        bookingStatusCommandService.advanceForFulfillment(order.getId(),
                BookingStatusCommandService.FulfillmentAdvance.DEPART);
        return view(recordMapper.selectById(record.getId()));
    }

    /**
     * 签到：EN_ROUTE → CHECKED_IN；坐标可选，超窗拒绝。
     *
     * @param workerId 人员 ID
     * @param orderNo  订单号
     * @param longitude 经度（可空）
     * @param latitude  纬度（可空）
     * @param source    GPS / MANUAL
     * @return 履约聚合视图
     */
    @Transactional
    public FulfillmentView checkIn(long workerId, String orderNo,
                                   BigDecimal longitude, BigDecimal latitude, String source) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        requireCurrentWorker(record, workerId);
        LocalDateTime now = LocalDateTime.now();
        if (order.getAppointmentStart() != null && order.getAppointmentEnd() != null) {
            boolean inWindow = !now.isBefore(order.getAppointmentStart()
                    .minus(CHECK_IN_BEFORE_MINUTES))
                    && !now.isAfter(order.getAppointmentEnd().plus(CHECK_IN_AFTER_MINUTES));
            if (!inWindow) {
                throw new BusinessException(ErrorCode.FULFILLMENT_CHECK_IN_OUT_OF_WINDOW);
            }
        }
        String sourceValue = source == null || source.isBlank() ? "GPS" : source;
        if (recordMapper.checkIn(record.getId(), workerId, longitude, latitude, sourceValue) == 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        // 签到不推进订单主状态（订单保持 EN_ROUTE），只同步履约摘要列
        bookingStatusCommandService.syncFulfillmentSummary(order.getId(),
                FulfillmentStatus.CHECKED_IN.name());
        return view(recordMapper.selectById(record.getId()));
    }

    /**
     * 开始服务：CHECKED_IN → IN_SERVICE；存在未处理安全异常阻断（docs 第 7.3 节）。
     *
     * @param workerId 人员 ID
     * @param orderNo  订单号
     * @return 履约聚合视图
     */
    @Transactional
    public FulfillmentView start(long workerId, String orderNo) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        requireCurrentWorker(record, workerId);
        if (exceptionMapper.countOpenSafety(record.getId()) > 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_OPEN_SAFETY_EXCEPTION);
        }
        if (recordMapper.start(record.getId(), workerId) == 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        bookingStatusCommandService.advanceForFulfillment(order.getId(),
                BookingStatusCommandService.FulfillmentAdvance.START);
        return view(recordMapper.selectById(record.getId()));
    }

    // ------------------------------------------------------------------
    // 检查项 / 证据（docs/modules/09 第 8、9 节）
    // ------------------------------------------------------------------

    /**
     * 完成检查项（重复完成幂等返回）。
     *
     * @param workerId 人员 ID
     * @param orderNo  订单号
     * @param itemId   检查项 ID
     * @param remark   备注（可空）
     * @return 履约聚合视图
     */
    @Transactional
    public FulfillmentView completeChecklistItem(long workerId, String orderNo,
                                                 long itemId, String remark) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        requireCurrentWorker(record, workerId);
        ServiceChecklistItemEntity item = checklistMapper.selectById(itemId);
        if (item == null || item.getServiceRecordId() != record.getId()) {
            throw new BusinessException(ErrorCode.FULFILLMENT_NOT_FOUND);
        }
        // 检查项在签到后至申请完成前填写（docs 第 8 节）
        if (!"CHECKED_IN".equals(record.getStatus()) && !"IN_SERVICE".equals(record.getStatus())) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        checklistMapper.complete(itemId, null, remark);
        return view(record);
    }

    /**
     * 上传证据（同一文件重复上传幂等返回，docs 第 16 节）。
     *
     * @param workerId      人员 ID
     * @param orderNo       订单号
     * @param evidenceType  证据类型
     * @param fileId        文件 ID
     * @param checklistItemId 关联检查项（可空）
     * @param description   说明（可空）
     * @param accountId     上传人账号 ID
     * @return 履约聚合视图
     */
    @Transactional
    public FulfillmentView uploadEvidence(long workerId, String orderNo, String evidenceType,
                                          long fileId, Long checklistItemId,
                                          String description, long accountId) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        requireCurrentWorker(record, workerId);
        ServiceEvidenceEntity existing = evidenceMapper.selectByFileId(fileId);
        if (existing != null) {
            if (existing.getServiceRecordId() != record.getId()) {
                throw new BusinessException(ErrorCode.FULFILLMENT_EVIDENCE_REQUIRED,
                        "文件已被其他订单使用");
            }
            return view(record);
        }
        if (checklistItemId != null) {
            ServiceChecklistItemEntity item = checklistMapper.selectById(checklistItemId);
            if (item == null || item.getServiceRecordId() != record.getId()) {
                throw new BusinessException(ErrorCode.FULFILLMENT_NOT_FOUND);
            }
        }
        ServiceEvidenceEntity evidence = new ServiceEvidenceEntity();
        evidence.setServiceRecordId(record.getId());
        evidence.setChecklistItemId(checklistItemId);
        evidence.setEvidenceType(evidenceType);
        evidence.setFileId(fileId);
        evidence.setDescription(description);
        evidence.setUploadedByAccountId(accountId);
        evidenceMapper.insert(evidence);
        return view(record);
    }

    // ------------------------------------------------------------------
    // 异常（docs/modules/09 第 10 节）
    // ------------------------------------------------------------------

    /**
     * 服务人员上报异常：履约转 EXCEPTION（订单主状态保持不变）。
     *
     * @param workerId      人员 ID
     * @param orderNo       订单号
     * @param exceptionType 异常类型
     * @param description   说明
     * @param accountId     上报人账号 ID
     * @return 履约聚合视图
     */
    @Transactional
    public FulfillmentView reportException(long workerId, String orderNo, String exceptionType,
                                           String description, long accountId) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        requireCurrentWorker(record, workerId);
        if (recordMapper.markException(record.getId(), workerId) == 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        ServiceExceptionEntity entity = new ServiceExceptionEntity();
        entity.setExceptionNo(noGenerator.next(SEQ_EXCEPTION, PREFIX_EXCEPTION));
        entity.setServiceRecordId(record.getId());
        entity.setExceptionType(exceptionType);
        entity.setDescription(description);
        entity.setStatus("OPEN");
        entity.setReportedByAccountId(accountId);
        entity.setCreatedByAccountId(accountId);
        exceptionMapper.insert(entity);
        bookingStatusCommandService.syncFulfillmentSummary(order.getId(),
                FulfillmentStatus.EXCEPTION.name());
        return view(recordMapper.selectById(record.getId()));
    }

    /**
     * 运营处理异常：RESOLVED 时履约回 IN_SERVICE 并恢复订单主状态。
     *
     * @param orderNo      订单号
     * @param exceptionNo  异常单号
     * @param accept       true=认可(RESOLVED) / false=驳回(REJECTED)
     * @param resolution   处理结论
     * @param accountId    处理人账号 ID
     * @return 处理后的异常视图
     */
    @Transactional
    public FulfillmentView.ExceptionView handleException(String orderNo, String exceptionNo,
                                                         boolean accept, String resolution,
                                                         long accountId) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        ServiceExceptionEntity exception = exceptionMapper.selectByExceptionNo(exceptionNo);
        if (exception == null || exception.getServiceRecordId() != record.getId()) {
            throw new BusinessException(ErrorCode.FULFILLMENT_NOT_FOUND);
        }
        String target = accept ? "RESOLVED" : "REJECTED";
        if (exceptionMapper.handle(exception.getId(), target, resolution, accountId) == 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        if (accept) {
            // 异常处理完成：履约回 IN_SERVICE（docs 第 5.2 节唯一恢复路径）
            recordMapper.resumeFromException(record.getId());
            bookingStatusCommandService.advanceForFulfillment(order.getId(),
                    BookingStatusCommandService.FulfillmentAdvance.RESUME);
        }
        return FulfillmentView.ExceptionView.from(
                exceptionMapper.selectById(exception.getId()));
    }

    // ------------------------------------------------------------------
    // 完成申请 / 客户确认 / 自动确认（docs/modules/09 第 11 节）
    // ------------------------------------------------------------------

    /**
     * 服务人员申请完成：校验必填检查项与证据（docs 第 11.1 节）。
     *
     * @param workerId 人员 ID
     * @param orderNo  订单号
     * @param serviceResult 服务结果说明（可空）
     * @return 履约聚合视图
     */
    @Transactional
    public FulfillmentView requestComplete(long workerId, String orderNo, String serviceResult) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        requireCurrentWorker(record, workerId);
        List<ServiceChecklistItemEntity> items = checklistMapper.selectByRecordId(record.getId());
        List<ServiceEvidenceEntity> evidences = evidenceMapper.selectByRecordId(record.getId());
        for (ServiceChecklistItemEntity item : items) {
            if (!Boolean.TRUE.equals(item.getCompleted())) {
                if (Boolean.TRUE.equals(item.getRequired())) {
                    throw new BusinessException(ErrorCode.FULFILLMENT_CHECKLIST_REQUIRED,
                            "必填检查项未完成：" + item.getItemName());
                }
            } else if (Boolean.TRUE.equals(item.getEvidenceRequired())
                    && evidences.stream().noneMatch(evidence ->
                            item.getId().equals(evidence.getChecklistItemId()))) {
                throw new BusinessException(ErrorCode.FULFILLMENT_EVIDENCE_REQUIRED,
                        "检查项缺少证据：" + item.getItemName());
            }
        }
        if (recordMapper.requestComplete(record.getId(), workerId,
                LocalDateTime.now().plus(AUTO_CONFIRM_HOURS), serviceResult) == 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        bookingStatusCommandService.advanceForFulfillment(order.getId(),
                BookingStatusCommandService.FulfillmentAdvance.REQUEST_COMPLETE);
        return view(recordMapper.selectById(record.getId()));
    }

    /**
     * 客户确认完成（docs 第 11.2 节）：仅订单归属客户本人可确认。
     *
     * @param customerAccountId 客户账号 ID
     * @param orderNo           订单号
     * @return 履约聚合视图
     * @throws BusinessException 非本人订单 / 状态冲突
     */
    @Transactional
    public FulfillmentView customerConfirm(long customerAccountId, String orderNo) {
        BookingOrderEntity order = requireOrder(orderNo);
        if (customerProfileService.requireUsable(customerAccountId).customerId()
                != order.getCustomerId()) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OWNED);
        }
        ServiceRecordEntity record = requireRecord(order.getId());
        if (recordMapper.complete(record.getId(), "CUSTOMER") == 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        bookingStatusCommandService.advanceForFulfillment(order.getId(),
                BookingStatusCommandService.FulfillmentAdvance.COMPLETE);
        return view(recordMapper.selectById(record.getId()));
    }

    /**
     * 自动确认到期记录（docs 第 11.3 节，定时任务驱动；幂等由条件更新保证）。
     *
     * @param asOf      视作当前时刻
     * @param batchSize 单批上限
     * @return 本轮确认数
     */
    @Transactional
    public int autoConfirmDue(LocalDateTime asOf, int batchSize) {
        List<ServiceRecordEntity> due = recordMapper.selectDueConfirmations(asOf, batchSize);
        int confirmed = 0;
        for (ServiceRecordEntity record : due) {
            if (recordMapper.complete(record.getId(), "SYSTEM") > 0) {
                bookingStatusCommandService.advanceForFulfillment(record.getBookingId(),
                        BookingStatusCommandService.FulfillmentAdvance.COMPLETE);
                confirmed++;
            }
        }
        return confirmed;
    }

    /**
     * 运营强制完成（docs 第 17.3 节）。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @Transactional
    public FulfillmentView forceComplete(String orderNo) {
        BookingOrderEntity order = requireOrder(orderNo);
        ServiceRecordEntity record = requireRecord(order.getId());
        if (recordMapper.forceComplete(record.getId()) == 0) {
            throw new BusinessException(ErrorCode.FULFILLMENT_STATUS_CONFLICT);
        }
        bookingStatusCommandService.advanceForFulfillment(order.getId(),
                BookingStatusCommandService.FulfillmentAdvance.FORCE_COMPLETE);
        return view(recordMapper.selectById(record.getId()));
    }

    /**
     * 订单取消时终止履约（订单模块取消链路调用）。
     *
     * @param bookingId 订单 ID
     */
    @Transactional
    public void cancelForBooking(long bookingId) {
        recordMapper.cancelForBooking(bookingId);
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /**
     * 按订单查询履约聚合（三端通用；归属校验由调用方完成）。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图或 null
     */
    @Transactional(readOnly = true)
    public FulfillmentView findByOrderNo(String orderNo) {
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            return null;
        }
        ServiceRecordEntity record = recordMapper.selectByBookingId(order.getId());
        return record == null ? null : view(record);
    }

    /**
     * 运营履约列表分页。
     *
     * @param status 状态过滤，可空
     * @param page   页码（1 起）
     * @param size   每页大小
     * @return 记录列表
     */
    @Transactional(readOnly = true)
    public List<ServiceRecordEntity> operatorList(String status, int page, int size) {
        return recordMapper.selectPage(status, Math.max(0, page - 1) * size, size);
    }

    /**
     * 运营履约列表总数。
     *
     * @param status 状态过滤，可空
     * @return 总数
     */
    @Transactional(readOnly = true)
    public long operatorCount(String status) {
        return recordMapper.count(status);
    }

    /**
     * 记录视图（运营详情含聚合）。
     *
     * @param orderNo 订单号
     * @return 履约聚合视图
     */
    @Transactional(readOnly = true)
    public FulfillmentView operatorDetail(String orderNo) {
        return view(requireRecord(requireOrder(orderNo).getId()));
    }

    /**
     * 客户视角履约详情（归属校验：仅本人订单可见，docs 第 4.2 节）。
     *
     * @param customerAccountId 客户账号 ID
     * @param orderNo           订单号
     * @return 履约聚合视图
     */
    @Transactional(readOnly = true)
    public FulfillmentView customerView(long customerAccountId, String orderNo) {
        BookingOrderEntity order = requireOrder(orderNo);
        if (customerProfileService.requireUsable(customerAccountId).customerId()
                != order.getCustomerId()) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OWNED);
        }
        ServiceRecordEntity record = recordMapper.selectByBookingId(order.getId());
        return record == null ? null : view(record);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private FulfillmentView view(ServiceRecordEntity record) {
        List<FulfillmentView.ChecklistItemView> checklist =
                checklistMapper.selectByRecordId(record.getId()).stream()
                        .map(FulfillmentView.ChecklistItemView::from).toList();
        List<FulfillmentView.EvidenceView> evidences =
                evidenceMapper.selectByRecordId(record.getId()).stream()
                        .map(FulfillmentView.EvidenceView::from).toList();
        List<FulfillmentView.ExceptionView> exceptions =
                exceptionMapper.selectByRecordId(record.getId()).stream()
                        .map(FulfillmentView.ExceptionView::from).toList();
        return FulfillmentView.from(record, checklist, evidences, exceptions);
    }

    private BookingOrderEntity requireOrder(String orderNo) {
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        return order;
    }

    private ServiceRecordEntity requireRecord(long bookingId) {
        ServiceRecordEntity record = recordMapper.selectByBookingId(bookingId);
        if (record == null) {
            throw new BusinessException(ErrorCode.FULFILLMENT_NOT_FOUND);
        }
        return record;
    }

    /** 只有当前分配人员可以推进履约（docs 第 2 节；改派后旧人员被挡）。 */
    private void requireCurrentWorker(ServiceRecordEntity record, long workerId) {
        if (record.getWorkerId() == null || record.getWorkerId() != workerId) {
            throw new BusinessException(ErrorCode.FULFILLMENT_NOT_OWNED);
        }
    }
}
