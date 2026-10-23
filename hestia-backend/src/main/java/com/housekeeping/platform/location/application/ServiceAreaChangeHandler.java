package com.housekeeping.platform.location.application;

/**
 * 服务区域变更回调（端口）。
 *
 * <p>服务区域的覆盖、优先级、状态或生效时间变化后，已匹配的客户地址会过期，
 * 必须被标记为 {@code STALE}。位置模块<b>不直接</b>更新 {@code customer_address}：
 * 那是客户模块的表，跨模块直接写库会让两个模块的约束（状态机、审计字段）互相踩。</p>
 *
 * <p>因此定义端口，由客户模块实现并在事务内被调用。单体内用同步回调，
 * 后续拆服务时替换为领域事件（Outbox）即可，位置模块无需改动。</p>
 */
public interface ServiceAreaChangeHandler {

    /**
     * 处理服务区域变更。
     *
     * @param serviceAreaId 发生变更的服务区域 ID
     * @param reason        变更原因，写入日志便于追溯
     */
    void onServiceAreaChanged(long serviceAreaId, String reason);
}
