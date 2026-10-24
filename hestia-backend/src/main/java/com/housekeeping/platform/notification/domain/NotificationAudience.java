package com.housekeeping.platform.notification.domain;

/**
 * 通知受众（模板选择键的一部分）。
 *
 * <p>同一事件面向不同受众的文案不同（客户看"支付成功"，人员看"新任务"），
 * 模板按（事件类型 + 受众 + 渠道）解析；任务上的 {@code recipient_role}
 * 记录收件人角色，满足 docs/modules/12 第 4 节
 * "同一账号拥有多个角色时，通知任务必须指定收件角色"的要求。</p>
 */
public enum NotificationAudience {

    /** 消费者。 */
    CUSTOMER,

    /** 服务人员。 */
    WORKER,

    /** 运营人员（预留：人工派单、退款待审等事件接入后启用）。 */
    OPERATOR
}
