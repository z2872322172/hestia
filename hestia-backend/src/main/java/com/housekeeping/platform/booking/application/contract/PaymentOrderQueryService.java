package com.housekeeping.platform.booking.application.contract;

import com.housekeeping.platform.booking.application.contract.PayableOrderRef;

/**
 * 支付侧订单查询契约（docs/07 §11.2 命名对齐）。
 *
 * <p>支付模块通过本接口取"可支付订单"，归属校验与状态校验都发生在订单模块内部。</p>
 */
public interface PaymentOrderQueryService {

    /**
     * 校验订单属于该客户且处于可支付状态。
     *
     * @param customerId 客户档案 ID
     * @param orderNo    订单号
     * @return 可支付订单摘要
     * @throws com.housekeeping.platform.common.exception.BusinessException
     *         BOOKING_NOT_FOUND / PAYMENT_ORDER_NOT_PAYABLE / PAYMENT_TIMEOUT
     */
    PayableOrderRef requirePayable(long customerId, String orderNo);

    /**
     * 校验订单属于该客户，返回订单 ID（不限状态，支付/退款流水查询用）。
     *
     * @param customerId 客户档案 ID
     * @param orderNo    订单号
     * @return 订单 ID
     * @throws com.housekeeping.platform.common.exception.BusinessException BOOKING_NOT_OWNED
     */
    long requireOwnedBookingId(long customerId, String orderNo);
}
