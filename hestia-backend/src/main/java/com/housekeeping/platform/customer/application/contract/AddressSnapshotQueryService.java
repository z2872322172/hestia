package com.housekeeping.platform.customer.application.contract;

import com.housekeeping.platform.customer.application.view.AddressSnapshot;

/**
 * 下单地址快照契约（docs/07 第 11.2 节、docs/modules/02 第 15 节）。
 *
 * <p>下单时由订单模块调用，把地址"拍平"进订单快照。之后客户改地址、删地址、
 * 甚至行政区改名，都不影响历史订单上的描述。</p>
 *
 * <p>入参用 {@code accountId} 而不是 {@code customerId}：归属校验必须由客户模块自己做，
 * 不能指望调用方先查一遍再传进来——那样漏一次就是越权读别人的地址。</p>
 */
public interface AddressSnapshotQueryService {

    /**
     * 生成地址快照。
     *
     * @param accountId 当前登录账号 ID（用于归属校验）
     * @param addressNo 地址编号
     * @return 地址快照
     * @throws com.housekeeping.platform.common.exception.BusinessException
     *         地址不存在、不属于该账号或已删除
     */
    AddressSnapshot requireSnapshot(long accountId, String addressNo);
}
