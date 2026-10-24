package com.housekeeping.platform.customer.application.contract;

/**
 * 客户查询契约，供其他模块（下单、调度等）按账号取客户身份。
 *
 * <p>只读。建档与关闭属于客户模块自己的写入流程，不对外开放。</p>
 */
public interface CustomerQueryService {

    /**
     * 按账号取<b>可用</b>的客户档案。
     *
     * @param accountId 账号 ID
     * @return 客户引用
     * @throws com.housekeeping.platform.common.exception.BusinessException
     *         档案不存在（{@code CUSTOMER_PROFILE_NOT_FOUND}）或已关闭（{@code CUSTOMER_PROFILE_CLOSED}）
     */
    CustomerRef requireUsable(long accountId);

    /**
     * 按客户档案 ID 反查账号 ID（通知模块按订单事件定位收件人使用）。
     *
     * <p>只回一个 ID，不暴露档案其余字段；档案不存在或已关闭都抛
     * {@code CUSTOMER_PROFILE_NOT_FOUND}——反查场景里"已关闭"等同于收不到通知。</p>
     *
     * @param customerId 客户档案 ID
     * @return 账号 ID
     * @throws com.housekeeping.platform.common.exception.BusinessException
     *         档案不存在（{@code CUSTOMER_PROFILE_NOT_FOUND}）
     */
    long requireAccountIdByCustomerId(long customerId);

    /**
     * 按客户业务编号反查账号 ID（通知冒烟链路用公开编号定位收件人）。
     *
     * <p>客户编号是对外可暴露的业务编号（客户档案接口可见），内部 ID 则不出模块；
     * 提供这个反查让冒烟脚本 / 运营工具不必接触内部主键。编号不存在抛
     * {@code CUSTOMER_PROFILE_NOT_FOUND}。</p>
     *
     * @param customerNo 客户业务编号（CUS 前缀）
     * @return 账号 ID
     * @throws com.housekeeping.platform.common.exception.BusinessException
     *         档案不存在（{@code CUSTOMER_PROFILE_NOT_FOUND}）
     */
    long requireAccountIdByCustomerNo(String customerNo);
}
