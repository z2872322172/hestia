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
}
