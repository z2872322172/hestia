package com.housekeeping.platform.booking.application;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.booking.application.command.BookingCreateCommand;
import com.housekeeping.platform.booking.application.view.BookingCreatedView;
import com.housekeeping.platform.booking.domain.BookingStatus;
import com.housekeeping.platform.booking.domain.CancellationFeeCalculator;
import com.housekeeping.platform.booking.domain.CancellationFeePolicy;
import com.housekeeping.platform.booking.persistence.entity.BookingCancellationEntity;
import com.housekeeping.platform.booking.persistence.entity.BookingItemEntity;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.booking.persistence.entity.BookingScheduleHistoryEntity;
import com.housekeeping.platform.booking.persistence.entity.BookingStatusLogEntity;
import com.housekeeping.platform.booking.persistence.mapper.BookingCancellationMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingItemMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingScheduleHistoryMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingStatusLogMapper;
import com.housekeeping.platform.catalog.application.PriceQuoteService;
import com.housekeeping.platform.catalog.application.ServiceCatalogQueryService;
import com.housekeeping.platform.catalog.application.command.PriceQuoteQuery;
import com.housekeeping.platform.catalog.application.view.PricePreviewView;
import com.housekeeping.platform.catalog.application.view.ServiceItemSnapshot;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.CustomerAddressService;
import com.housekeeping.platform.customer.application.CustomerProfileService;
import com.housekeeping.platform.customer.application.contract.CustomerRef;
import com.housekeeping.platform.customer.application.view.AddressSnapshot;
import com.housekeeping.platform.dispatch.application.DispatchApplicationService;
import com.housekeeping.platform.infrastructure.persistence.idempotency.IdempotencyService;
import com.housekeeping.platform.infrastructure.persistence.sequence.BusinessNoGenerator;
import com.housekeeping.platform.location.application.ServiceAreaMatchService;
import com.housekeeping.platform.location.domain.ServiceAreaMatchQuery;
import com.housekeeping.platform.location.domain.ServiceAreaMatchResult;
import com.housekeeping.platform.payment.application.RefundApplicationService;
import com.housekeeping.platform.scheduling.application.AvailabilityQueryService;
import com.housekeeping.platform.scheduling.application.ServiceWindowService;
import com.housekeeping.platform.scheduling.application.WorkerSlotReservationService;
import com.housekeeping.platform.scheduling.application.view.ServiceWindowView;
import com.housekeeping.platform.scheduling.domain.WindowTimeline;
import com.housekeeping.platform.scheduling.persistence.entity.WorkerSlotEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 订单应用服务：下单编排、取消与改期（docs/modules/07 第 7、10、11 节）。
 *
 * <p>下单是全平台最长的单事务链路，顺序固定（docs 第 7 节）：</p>
 *
 * <pre>
 * 客户档案 → 地址快照 → 服务区域匹配 → 服务项目快照 → 时间窗 → 报价
 * → 候选人员（时段库存 → 接单上限 → 资格过滤）→ 写订单 → 锁时段 → 明细/日志 → 建派单
 * </pre>
 *
 * <p>并发要点：先插订单（worker_slot_id 暂记 0，本事务未提交对外不可见），
 * 拿到主键后锁时段，再把真实 slot id 回填——锁时段需要 bookingId，
 * 订单号生成又依赖订单主键，这个次序不能颠倒。锁失败抛异常让整条链路回滚，
 * 时段与订单都不残留。</p>
 */
@Service
@RequiresPersistence
public class BookingApplicationService {

    private static final Logger log = LoggerFactory.getLogger(BookingApplicationService.class);

    private static final String SEQ_BOOKING = "BOOKING";

    private static final String PREFIX_BOOKING = "BKG";

    private static final String CREATE_API_PATH = "/api/v1/customer/bookings";

    private final BookingProperties properties;

    private final BookingOrderMapper orderMapper;

    private final BookingItemMapper itemMapper;

    private final BookingStatusLogMapper statusLogMapper;

    private final BookingScheduleHistoryMapper historyMapper;

    private final BookingCancellationMapper cancellationMapper;

    private final CustomerProfileService customerProfileService;

    private final CustomerAddressService customerAddressService;

    private final ServiceAreaMatchService serviceAreaMatchService;

    private final ServiceCatalogQueryService catalogQueryService;

    private final PriceQuoteService priceQuoteService;

    private final ServiceWindowService windowService;

    private final AvailabilityQueryService availabilityQueryService;

    private final WorkerSlotReservationService slotReservation;

    private final DispatchApplicationService dispatchService;

    private final RefundApplicationService refundService;

    private final IdempotencyService idempotencyService;

    private final BusinessNoGenerator noGenerator;

    private final ObjectMapper objectMapper;

    private final BookingEventPublisher eventPublisher;

    public BookingApplicationService(BookingProperties properties,
                                     BookingOrderMapper orderMapper,
                                     BookingItemMapper itemMapper,
                                     BookingStatusLogMapper statusLogMapper,
                                     BookingScheduleHistoryMapper historyMapper,
                                     BookingCancellationMapper cancellationMapper,
                                     CustomerProfileService customerProfileService,
                                     CustomerAddressService customerAddressService,
                                     ServiceAreaMatchService serviceAreaMatchService,
                                     ServiceCatalogQueryService catalogQueryService,
                                     PriceQuoteService priceQuoteService,
                                     ServiceWindowService windowService,
                                     AvailabilityQueryService availabilityQueryService,
                                     WorkerSlotReservationService slotReservation,
                                     DispatchApplicationService dispatchService,
                                     RefundApplicationService refundService,
                                     IdempotencyService idempotencyService,
                                     BusinessNoGenerator noGenerator,
                                     ObjectMapper objectMapper,
                                     BookingEventPublisher eventPublisher) {
        this.properties = properties;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.statusLogMapper = statusLogMapper;
        this.historyMapper = historyMapper;
        this.cancellationMapper = cancellationMapper;
        this.customerProfileService = customerProfileService;
        this.customerAddressService = customerAddressService;
        this.serviceAreaMatchService = serviceAreaMatchService;
        this.catalogQueryService = catalogQueryService;
        this.priceQuoteService = priceQuoteService;
        this.windowService = windowService;
        this.availabilityQueryService = availabilityQueryService;
        this.slotReservation = slotReservation;
        this.dispatchService = dispatchService;
        this.refundService = refundService;
        this.idempotencyService = idempotencyService;
        this.noGenerator = noGenerator;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 预览价格（docs/modules/07 第 18.5 节的 preview 接口）。
     *
     * @param command 下单命令（只取项目、数量与规格参与计价）
     * @return 报价预览
     */
    @Transactional(readOnly = true)
    public PricePreviewView preview(BookingCreateCommand command) {
        return priceQuoteService.preview(command.serviceItemNo(),
                new PriceQuoteQuery(command.quantity(), command.options()));
    }

    /**
     * 创建订单（可带幂等键）。
     *
     * <p>幂等协议见 {@code IdempotencyService}：同键同内容已成功 → 直接回放；
     * 占位插入与业务写库同事务，失败一起回滚，客户端可安全重试。</p>
     *
     * @param accountId      客户账号 ID
     * @param idempotencyKey 幂等键，可空
     * @param command        下单命令
     * @return 创建结果
     */
    @Transactional
    public BookingCreatedView create(long accountId, String idempotencyKey, BookingCreateCommand command) {
        String requestJson = writeJson(command);
        String requestHash = IdempotencyService.hashRequest(requestJson);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<String> replayed = idempotencyService.findSucceededResponse(
                    accountId, CREATE_API_PATH, idempotencyKey, requestHash);
            if (replayed.isPresent()) {
                return readCreatedView(replayed.get());
            }
            idempotencyService.begin(accountId, CREATE_API_PATH, idempotencyKey, requestHash);
        }
        BookingCreatedView created = doCreate(accountId, command);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyService.markSucceeded(idempotencyKey, writeJson(created));
        }
        return created;
    }

    /**
     * 取消订单（docs/modules/07 第 10 节）。
     *
     * <p>待支付取消只释放时段；已支付取消按订单上的取消规则快照计算违约金
     * 并落取消记录（每单一条，唯一键兜底）。</p>
     *
     * @param accountId 客户账号 ID
     * @param orderNo   订单号
     * @param reasonCode 取消原因编码，可空
     * @param reasonText 取消原因描述，可空
     */
    @Transactional
    public void cancel(long accountId, String orderNo, String reasonCode, String reasonText) {
        CustomerRef customer = customerProfileService.requireUsable(accountId);
        BookingOrderEntity order = requireOwnedOrder(orderNo, customer.customerId());
        BookingStatus status = BookingStatus.of(order.getOrderStatus());
        if (!status.cancellable()) {
            throw new BusinessException(ErrorCode.BOOKING_CANNOT_CANCEL);
        }
        String fromStatus = status.name();
        if (orderMapper.cancel(order.getId(), fromStatus, reasonCode, reasonText) == 0) {
            throw new BusinessException(ErrorCode.BOOKING_STATUS_CONFLICT);
        }
        WorkerSlotEntity slot = slotReservation.findByBooking(order.getId());
        if (status == BookingStatus.PENDING_PAYMENT) {
            if (slot != null) {
                slotReservation.releaseHeld(slot.getId(), order.getId());
            }
        } else {
            // 已支付取消：费用按订单快照计算，结果落 booking_cancellation（docs 第 10.2 节）
            if (slot != null) {
                slotReservation.releaseBooked(slot.getId(), order.getId());
            }
            CancellationFeePolicy policy = readCancellationPolicy(order);
            var fee = CancellationFeeCalculator.calculate(order.getPayableAmount(),
                    order.getAppointmentStart(), LocalDateTime.now(), policy);
            BookingCancellationEntity cancellation = new BookingCancellationEntity();
            cancellation.setBookingId(order.getId());
            cancellation.setReasonCode(reasonCode);
            cancellation.setReasonText(reasonText);
            cancellation.setCanceledByAccountId(accountId);
            cancellation.setCanceledByRole("CUSTOMER");
            cancellation.setFeeAmount(fee.feeAmount());
            cancellation.setRefundableAmount(fee.refundableAmount());
            cancellation.setPolicySnapshotJson(order.getCancellationPolicySnapshotJson());
            // uk_booking_cancellation_order：并发下第二次取消先被状态条件更新挡住，这里只兜极小窗口
            cancellationMapper.insert(cancellation);
            // 有剩余可退金额时生成退款申请（无成功支付则静默跳过），摘要置 REFUNDING
            if (fee.refundableAmount().signum() > 0) {
                boolean refundCreated = refundService.createForBookingCancellation(order.getId(),
                        fee.refundableAmount(), reasonCode, reasonText, accountId) != null;
                if (refundCreated) {
                    BookingOrderEntity patch = new BookingOrderEntity();
                    patch.setId(order.getId());
                    patch.setPaymentStatus("REFUNDING");
                    orderMapper.updateById(patch);
                }
            }
        }
        dispatchService.cancelForBooking(order.getId());
        syncAssignmentSummary(order.getId(), "CANCELED");
        writeStatusLog(order.getId(), fromStatus, BookingStatus.CANCELED.name(),
                accountId, "USER", reasonCode, reasonText);
        // 事件与取消同一事务落 outbox，Relay 异步投递（docs/04 第 10.5 节）
        eventPublisher.publishCanceled(order, reasonCode, reasonText, "CUSTOMER");
    }

    /**
     * 同步派单摘要列：订单详情的 assignment_status 读自本列，
     * 派单状态推进（激活/取消）时必须同步，否则运营监控读到滞后状态。
     */
    private void syncAssignmentSummary(long bookingId, String assignmentStatus) {
        BookingOrderEntity patch = new BookingOrderEntity();
        patch.setId(bookingId);
        patch.setAssignmentStatus(assignmentStatus);
        orderMapper.updateById(patch);
    }

    /**
     * 改期（docs/modules/07 第 11 节）：仅已支付且未出发的订单。
     *
     * <p>同一事务内：锁新时段 → 更新订单 → 释放旧时段 → 写改期历史 → 改派。
     * 任一步失败整体回滚，原订单与原时段不变。</p>
     *
     * @param accountId      客户账号 ID
     * @param orderNo        订单号
     * @param appointmentDate 新服务日期
     * @param serviceWindowCode 新时间窗编码
     */
    @Transactional
    public void reschedule(long accountId, String orderNo,
                           LocalDate appointmentDate, String serviceWindowCode) {
        CustomerRef customer = customerProfileService.requireUsable(accountId);
        BookingOrderEntity order = requireOwnedOrder(orderNo, customer.customerId());
        BookingStatus status = BookingStatus.of(order.getOrderStatus());
        if (!status.reschedulable()) {
            throw new BusinessException(ErrorCode.BOOKING_CANNOT_RESCHEDULE);
        }
        if (appointmentDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        ServiceItemSnapshot item = catalogQueryService.requireSnapshot(order.getServiceItemNoSnapshot());
        ServiceWindowView window = windowService.requireActiveByCode(serviceWindowCode);
        if (!WindowTimeline.fits(item.durationMinutes(), window.startTime(), window.endTime())) {
            throw new BusinessException(ErrorCode.SERVICE_DURATION_EXCEEDS_WINDOW);
        }
        // 与下单同一条选人链路：时段库存 → 接单上限 → 资格过滤；无合格人员即该时段不可约
        List<Long> candidates = availabilityQueryService.availableWorkerIds(
                order.getServiceItemNoSnapshot(), order.getServiceAreaId(),
                appointmentDate, window.windowId());
        long newWorkerId = candidates.get(0);
        LocalDateTime newStart = LocalDateTime.of(appointmentDate, window.startTime());
        LocalDateTime newEnd = newStart.plusMinutes(item.durationMinutes());
        WorkerSlotEntity newSlot = slotReservation.hold(
                newWorkerId, appointmentDate, window.windowId(), order.getId());
        if (orderMapper.reschedule(order.getId(), appointmentDate, window.windowId(),
                window.windowName(), newStart, newEnd, newSlot.getId(), newWorkerId) == 0) {
            throw new BusinessException(ErrorCode.BOOKING_STATUS_CONFLICT);
        }
        slotReservation.releaseBooked(order.getWorkerSlotId(), order.getId());
        writeScheduleHistory(order, appointmentDate, window, newSlot.getId(), newWorkerId, accountId);
        dispatchService.rescheduleForBooking(order.getId(), newWorkerId, accountId);
        // 事件与改期同一事务落 outbox；order 实体仍是改期前快照，恰好用于记录原值
        eventPublisher.publishRescheduled(order, appointmentDate, serviceWindowCode, newWorkerId);
    }

    // ------------------------------------------------------------------
    // 创建订单主链路（docs/modules/07 第 7 节）
    // ------------------------------------------------------------------

    private BookingCreatedView doCreate(long accountId, BookingCreateCommand command) {
        if (command.appointmentDate() == null || command.appointmentDate().isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        CustomerRef customer = customerProfileService.requireUsable(accountId);
        AddressSnapshot address = customerAddressService.requireSnapshot(accountId, command.addressNo());
        ServiceAreaMatchResult areaMatch = serviceAreaMatchService.match(new ServiceAreaMatchQuery(
                address.cityCode(), address.districtCode(), address.streetCode(),
                address.longitude() == null ? null : address.longitude().doubleValue(),
                address.latitude() == null ? null : address.latitude().doubleValue()));
        if (areaMatch.matchStatus() != com.housekeeping.platform.location.domain.AreaMatchStatus.MATCHED) {
            // 用匹配结果而不是地址上的片区：下单那一刻的覆盖口径才是有效口径
            throw new BusinessException(ErrorCode.BOOKING_SERVICE_AREA_NOT_MATCHED);
        }
        ServiceItemSnapshot item = catalogQueryService.requireSnapshot(command.serviceItemNo());
        ServiceWindowView window = windowService.requireActiveByCode(command.serviceWindowCode());
        if (!WindowTimeline.fits(item.durationMinutes(), window.startTime(), window.endTime())) {
            throw new BusinessException(ErrorCode.SERVICE_DURATION_EXCEEDS_WINDOW);
        }
        PricePreviewView price = priceQuoteService.preview(command.serviceItemNo(),
                new PriceQuoteQuery(command.quantity(), command.options()));
        List<Long> candidates = availabilityQueryService.availableWorkerIds(
                command.serviceItemNo(), areaMatch.serviceAreaId(),
                command.appointmentDate(), window.windowId());

        LocalDateTime now = LocalDateTime.now();
        BookingOrderEntity order = assembleOrder(accountId, customer, address, areaMatch,
                item, window, price, command, now);
        // 1) 先插订单拿主键：锁时段需要 bookingId；worker_slot_id 暂记 0，
        //    本事务未提交对外不可见，锁成功后立即回填
        orderMapper.insert(order);
        // 2) 锁定第一名候选的时段：失败抛异常，整个事务回滚（订单与占位都不残留）
        WorkerSlotEntity slot = slotReservation.hold(
                candidates.get(0), command.appointmentDate(), window.windowId(), order.getId());
        orderMapper.update(null, new LambdaUpdateWrapper<BookingOrderEntity>()
                .eq(BookingOrderEntity::getId, order.getId())
                .set(BookingOrderEntity::getWorkerSlotId, slot.getId()));
        // 3) 明细、状态日志与派单任务
        insertItems(order.getId(), item, price, accountId);
        writeStatusLog(order.getId(), null, BookingStatus.PENDING_PAYMENT.name(),
                accountId, "USER", null, null);
        dispatchService.createForBooking(order.getId(), candidates, candidates.get(0), accountId);
        // 事件与下单同一事务落 outbox；幂等重放路径在 doCreate 之前返回，不会重复发
        eventPublisher.publishCreated(order);
        return new BookingCreatedView(order.getOrderNo(), order.getOrderStatus(),
                order.getPayableAmount(), order.getPaymentDeadline());
    }

    private BookingOrderEntity assembleOrder(long accountId, CustomerRef customer,
                                             AddressSnapshot address,
                                             ServiceAreaMatchResult areaMatch,
                                             ServiceItemSnapshot item,
                                             ServiceWindowView window,
                                             PricePreviewView price,
                                             BookingCreateCommand command,
                                             LocalDateTime now) {
        BookingOrderEntity order = new BookingOrderEntity();
        order.setOrderNo(noGenerator.next(SEQ_BOOKING, PREFIX_BOOKING));
        order.setCustomerId(customer.customerId());
        order.setCustomerNoSnapshot(customer.customerNo());
        order.setServiceItemId(item.serviceItemId());
        order.setServiceItemVersion(item.serviceItemVersion());
        order.setServiceItemNoSnapshot(item.serviceItemNo());
        order.setServiceNameSnapshot(item.serviceItemName());
        order.setServiceAreaId(areaMatch.serviceAreaId());
        order.setServiceAreaNameSnapshot(areaMatch.areaName());
        order.setAddressId(address.addressId());
        order.setAddressVersion(address.addressVersion());
        order.setAddressSnapshotJson(writeJson(address));
        order.setContactNameSnapshot(address.contactName());
        order.setContactPhoneSnapshot(address.contactPhone());
        order.setAppointmentDate(command.appointmentDate());
        order.setServiceWindowId(window.windowId());
        order.setServiceWindowNameSnapshot(window.windowName());
        order.setAppointmentStart(LocalDateTime.of(command.appointmentDate(), window.startTime()));
        order.setAppointmentEnd(LocalDateTime.of(command.appointmentDate(), window.startTime())
                .plusMinutes(price.durationMinutes()));
        order.setWorkerSlotId(0L);
        order.setCurrentWorkerId(null);
        order.setOrderStatus(BookingStatus.PENDING_PAYMENT.name());
        order.setPaymentStatus("UNPAID");
        order.setAssignmentStatus("WAITING_PAYMENT");
        order.setFulfillmentStatus("NOT_STARTED");
        order.setAfterSaleStatus("NONE");
        order.setReviewStatus("NOT_REVIEWED");
        order.setServiceAmount(price.baseAmount());
        order.setAddonAmount(price.addonAmount());
        order.setMaterialAmount(price.materialAmount());
        order.setTravelFee(price.travelFee());
        order.setDiscountAmount(price.discountAmount());
        order.setPayableAmount(price.payableAmount());
        order.setPaidAmount(BigDecimal.ZERO);
        order.setRefundedAmount(BigDecimal.ZERO);
        order.setCurrency("CNY");
        order.setPriceSnapshotJson(writeJson(price));
        order.setServiceStandardSnapshotJson(writeJson(item));
        order.setCancellationPolicySnapshotJson(writeJson(new CancellationFeePolicy(
                properties.getFreeCancelHours(), properties.getLateCancelFeeRate(),
                properties.getLateCancelFeeAmount())));
        order.setPaymentDeadline(now.plusMinutes(properties.getPaymentTimeoutMinutes()));
        order.setSource("CUSTOMER");
        order.setCreatedByAccountId(accountId);
        order.setUpdatedByAccountId(accountId);
        return order;
    }

    private void insertItems(long bookingId, ServiceItemSnapshot item, PricePreviewView price,
                             Long accountId) {
        int sortOrder = 1;
        List<BookingItemEntity> items = new ArrayList<>();
        addItem(items, bookingId, "BASE", null, item.serviceItemName(),
                price.baseAmount(), accountId, sortOrder++);
        addItem(items, bookingId, "ADDON", null, "附加服务",
                price.addonAmount(), accountId, sortOrder++);
        addItem(items, bookingId, "MATERIAL", null, "耗材费",
                price.materialAmount(), accountId, sortOrder++);
        addItem(items, bookingId, "TRAVEL", null, "上门服务费",
                price.travelFee(), accountId, sortOrder++);
        addItem(items, bookingId, "DISCOUNT", null, "优惠减免",
                price.discountAmount(), accountId, sortOrder);
        for (BookingItemEntity entity : items) {
            itemMapper.insert(entity);
        }
    }

    private void addItem(List<BookingItemEntity> items, long bookingId, String itemType,
                         String itemCode, String itemName, BigDecimal amount,
                         Long accountId, int sortOrder) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BookingItemEntity entity = new BookingItemEntity();
        entity.setBookingId(bookingId);
        entity.setItemType(itemType);
        entity.setItemCode(itemCode);
        entity.setItemNameSnapshot(itemName);
        entity.setUnitPriceSnapshot(amount);
        entity.setQuantity(BigDecimal.ONE);
        entity.setAmount(amount);
        entity.setSortOrder(sortOrder);
        entity.setCreatedByAccountId(accountId);
        items.add(entity);
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private BookingOrderEntity requireOwnedOrder(String orderNo, long customerId) {
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        // 越权与不存在同一响应，避免通过错误码差异探测他人订单号
        if (order == null || order.getCustomerId() != customerId) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OWNED);
        }
        return order;
    }

    private void writeStatusLog(long bookingId, String fromStatus, String toStatus,
                                Long operatorId, String operationSource,
                                String reasonCode, String reasonText) {
        BookingStatusLogEntity logEntity = new BookingStatusLogEntity();
        logEntity.setBookingId(bookingId);
        logEntity.setFromStatus(fromStatus);
        logEntity.setToStatus(toStatus);
        logEntity.setOperatorId(operatorId);
        logEntity.setOperatorRole(operationSource);
        logEntity.setOperationSource(operationSource);
        logEntity.setReasonCode(reasonCode);
        logEntity.setReasonText(reasonText);
        statusLogMapper.insert(logEntity);
    }

    private void writeScheduleHistory(BookingOrderEntity order, LocalDate newDate,
                                      ServiceWindowView window, Long newSlotId,
                                      Long newWorkerId, Long accountId) {
        BookingScheduleHistoryEntity history = new BookingScheduleHistoryEntity();
        history.setBookingId(order.getId());
        history.setOldAppointmentDate(order.getAppointmentDate());
        history.setOldServiceWindowId(order.getServiceWindowId());
        history.setOldWorkerSlotId(order.getWorkerSlotId());
        history.setOldWorkerId(order.getCurrentWorkerId());
        history.setNewAppointmentDate(newDate);
        history.setNewServiceWindowId(window.windowId());
        history.setNewWorkerSlotId(newSlotId);
        history.setNewWorkerId(newWorkerId);
        history.setOperatorId(accountId);
        historyMapper.insert(history);
    }

    private CancellationFeePolicy readCancellationPolicy(
            BookingOrderEntity order) {
        try {
            return objectMapper.readValue(order.getCancellationPolicySnapshotJson(),
                    CancellationFeePolicy.class);
        } catch (Exception exception) {
            throw new IllegalStateException("取消规则快照解析失败: " + order.getOrderNo(), exception);
        }
    }

    private BookingCreatedView readCreatedView(String json) {
        try {
            return objectMapper.readValue(json, BookingCreatedView.class);
        } catch (Exception exception) {
            throw new IllegalStateException("幂等响应回放解析失败", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("JSON 序列化失败", exception);
        }
    }
}
