package com.housekeeping.platform.booking.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.booking.application.contract.PayableOrderRef;
import com.housekeeping.platform.booking.application.contract.PaymentOrderQueryService;
import com.housekeeping.platform.booking.application.view.BookingDetailView;
import com.housekeeping.platform.booking.application.view.BookingItemView;
import com.housekeeping.platform.booking.application.view.BookingSummaryView;
import com.housekeeping.platform.booking.domain.BookingStatus;
import com.housekeeping.platform.booking.persistence.entity.BookingItemEntity;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.booking.persistence.mapper.BookingItemMapper;
import com.housekeeping.platform.booking.persistence.mapper.BookingOrderMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.api.PageResult;
import com.housekeeping.platform.common.exception.BusinessException;
import com.housekeeping.platform.common.persistence.RequiresPersistence;
import com.housekeeping.platform.customer.application.CustomerProfileService;
import com.housekeeping.platform.customer.application.view.AddressSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单查询服务：客户 / 人员 / 运营三个视角（docs/modules/07 第 18 节）。
 *
 * <p>越权口径与下单侧一致：客户视角只能看自己的订单，人员视角只能看
 * 分派给自己的订单，订单不存在与无权访问返回同一个错误码。
 * 详情中的地址、联系人等一律读订单自己的快照列——订单一旦创建，
 * 展示口径就应与下单那一刻一致，不受客户后续改地址影响。</p>
 */
@Service
@RequiresPersistence
public class BookingQueryService implements PaymentOrderQueryService {

    private final BookingOrderMapper orderMapper;

    private final BookingItemMapper itemMapper;

    private final CustomerProfileService customerProfileService;

    private final ObjectMapper objectMapper;

    public BookingQueryService(BookingOrderMapper orderMapper,
                               BookingItemMapper itemMapper,
                               CustomerProfileService customerProfileService,
                               ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.customerProfileService = customerProfileService;
        this.objectMapper = objectMapper;
    }

    /**
     * 支付模块契约：校验订单属于该客户且可支付（docs/modules/10 第 7.1 节）。
     *
     * <p>可支付 = PENDING_PAYMENT 且未过支付截止时间。金额不在这里返回给客户端确认，
     * 客户端金额只用于展示，支付金额永远以 {@link PayableOrderRef#payableAmount()} 为准。</p>
     */
    @Override
    @Transactional(readOnly = true)
    public PayableOrderRef requirePayable(long customerId, String orderNo) {
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || order.getCustomerId() != customerId) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OWNED);
        }
        if (!BookingStatus.PENDING_PAYMENT.name().equals(order.getOrderStatus())) {
            throw new BusinessException(ErrorCode.PAYMENT_ORDER_NOT_PAYABLE);
        }
        if (order.getPaymentDeadline() != null
                && order.getPaymentDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.PAYMENT_TIMEOUT);
        }
        return new PayableOrderRef(order.getId(), order.getOrderNo(),
                order.getPayableAmount(), order.getPaymentDeadline());
    }

    /**
     * 客户订单列表。
     *
     * @param accountId 客户账号 ID（内部转换为客户 ID）
     * @param page      页码
     * @param size      每页大小
     * @param status    主状态过滤，可空
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<BookingSummaryView> customerList(long accountId, int page, int size,
                                                       String status) {
        long customerId = customerProfileService.requireUsable(accountId).customerId();
        LambdaQueryWrapper<BookingOrderEntity> wrapper = new LambdaQueryWrapper<BookingOrderEntity>()
                .eq(BookingOrderEntity::getCustomerId, customerId)
                .eq(status != null && !status.isBlank(), BookingOrderEntity::getOrderStatus, status)
                .orderByDesc(BookingOrderEntity::getCreatedAt);
        return toSummaryPage(orderMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /**
     * 客户订单详情（仅本人）。
     *
     * @param accountId 客户账号 ID
     * @param orderNo   订单号
     * @return 订单详情
     * @throws BusinessException 订单不存在或不属于该客户
     */
    @Transactional(readOnly = true)
    public BookingDetailView customerDetail(long accountId, String orderNo) {
        long customerId = customerProfileService.requireUsable(accountId).customerId();
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null || order.getCustomerId() != customerId) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OWNED);
        }
        return detail(order);
    }

    /**
     * 人员任务列表（按当前分配人员过滤）。
     *
     * @param workerId 人员 ID
     * @param page     页码
     * @param size     每页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<BookingSummaryView> workerList(long workerId, int page, int size) {
        LambdaQueryWrapper<BookingOrderEntity> wrapper = new LambdaQueryWrapper<BookingOrderEntity>()
                .eq(BookingOrderEntity::getCurrentWorkerId, workerId)
                .orderByDesc(BookingOrderEntity::getAppointmentStart);
        return toSummaryPage(orderMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /**
     * 人员任务详情（仅分派给本人）。
     *
     * @param workerId 人员 ID
     * @param orderNo  订单号
     * @return 订单详情
     * @throws BusinessException 订单不存在或未分派给该人员
     */
    @Transactional(readOnly = true)
    public BookingDetailView workerDetail(long workerId, String orderNo) {
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        // 人员只能看到分派给自己的订单；未分派/他人订单一律"不存在"
        if (order == null
                || order.getCurrentWorkerId() == null
                || order.getCurrentWorkerId() != workerId) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_OWNED);
        }
        return detail(order);
    }

    /**
     * 运营订单列表（状态 / 服务日期过滤）。
     *
     * @param status          主状态过滤，可空
     * @param appointmentDate 服务日期过滤，可空
     * @param page            页码
     * @param size            每页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PageResult<BookingSummaryView> operatorList(String status,
                                                       java.time.LocalDate appointmentDate,
                                                       int page, int size) {
        LambdaQueryWrapper<BookingOrderEntity> wrapper = new LambdaQueryWrapper<BookingOrderEntity>()
                .eq(status != null && !status.isBlank(), BookingOrderEntity::getOrderStatus, status)
                .eq(appointmentDate != null, BookingOrderEntity::getAppointmentDate, appointmentDate)
                .orderByDesc(BookingOrderEntity::getCreatedAt);
        return toSummaryPage(orderMapper.selectPage(new Page<>(page, size), wrapper));
    }

    /**
     * 运营订单详情（无归属限制）。
     *
     * @param orderNo 订单号
     * @return 订单详情
     * @throws BusinessException 订单不存在
     */
    @Transactional(readOnly = true)
    public BookingDetailView operatorDetail(String orderNo) {
        BookingOrderEntity order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_FOUND);
        }
        return detail(order);
    }

    /**
     * 组装详情视图（各视角共用，全部取订单快照列）。
     *
     * @param order 订单实体
     * @return 详情视图
     */
    public BookingDetailView detail(BookingOrderEntity order) {
        List<BookingItemView> items = itemMapper.selectByBookingId(order.getId()).stream()
                .map(this::toItemView)
                .toList();
        String detailAddress = resolveAddress(order);
        return new BookingDetailView(order.getOrderNo(),
                order.getCustomerId(),
                order.getCustomerNoSnapshot(),
                order.getOrderStatus(),
                order.getPaymentStatus(),
                order.getAssignmentStatus(),
                order.getFulfillmentStatus(),
                order.getAfterSaleStatus(),
                order.getReviewStatus(),
                order.getServiceItemNoSnapshot(),
                order.getServiceNameSnapshot(),
                order.getServiceItemVersion(),
                order.getServiceAreaId(),
                order.getServiceAreaNameSnapshot(),
                order.getAppointmentDate(),
                order.getServiceWindowNameSnapshot(),
                order.getAppointmentStart(),
                order.getAppointmentEnd(),
                order.getCurrentWorkerId(),
                order.getWorkerSlotId(),
                order.getServiceAmount(),
                order.getAddonAmount(),
                order.getMaterialAmount(),
                order.getTravelFee(),
                order.getDiscountAmount(),
                order.getPayableAmount(),
                order.getPaidAmount(),
                order.getRefundedAmount(),
                order.getCurrency(),
                order.getContactNameSnapshot(),
                order.getContactPhoneSnapshot(),
                detailAddress,
                order.getCancelReasonCode(),
                order.getCancelReasonText(),
                order.getPaymentDeadline(),
                order.getSource(),
                items,
                order.getCreatedAt(),
                order.getPaidAt(),
                order.getCanceledAt());
    }

    /**
     * 从地址快照 JSON 还原详址；快照异常不阻塞详情展示。
     */
    private String resolveAddress(BookingOrderEntity order) {
        try {
            AddressSnapshot address = objectMapper.readValue(order.getAddressSnapshotJson(),
                    AddressSnapshot.class);
            return address.detailAddress();
        } catch (Exception exception) {
            return null;
        }
    }

    private BookingItemView toItemView(BookingItemEntity entity) {
        return new BookingItemView(entity.getItemType(), entity.getItemCode(),
                entity.getItemNameSnapshot(), entity.getUnitPriceSnapshot(),
                entity.getQuantity(), entity.getAmount());
    }

    private PageResult<BookingSummaryView> toSummaryPage(Page<BookingOrderEntity> result) {
        List<BookingSummaryView> records = result.getRecords().stream()
                .map(order -> new BookingSummaryView(order.getOrderNo(),
                        order.getServiceNameSnapshot(), order.getOrderStatus(),
                        order.getPaymentStatus(), order.getAppointmentDate(),
                        order.getAppointmentStart(), order.getPayableAmount(),
                        order.getCreatedAt()))
                .toList();
        return new PageResult<>(records, (int) result.getCurrent(), (int) result.getSize(),
                result.getTotal());
    }
}
