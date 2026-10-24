package com.housekeeping.platform.notification.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 异步通知任务，对应表 {@code notification_task}（docs/modules/12 第 12.2 节）。
 *
 * <p>任务由事件消费方创建（业务事务外的异步侧），由调度器认领并发送。
 * 不变量：{@code dedupe_key} 唯一索引保证"同一事件 + 收件人 + 模板 + 渠道"
 * 只会有一条任务，事件重复消费在插入时被唯一键挡下；
 * 状态推进全部走 Mapper 的条件更新，不使用 {@code updateById}。</p>
 */
@Data
@TableName("notification_task")
public class NotificationTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 任务业务编号，形如 NTK20261023000001。 */
    private String taskNo;

    /** 来源事件 ID（事件信封 eventId），去重键的组成部分。 */
    private String sourceEventId;

    private String eventType;

    private String templateCode;

    /** 收件账号 ID。 */
    private Long recipientAccountId;

    /** 收件角色：CUSTOMER / WORKER / OPERATOR。 */
    private String recipientRole;

    /** 发送渠道，首版只有 IN_APP。 */
    private String channel;

    /** 模板变量（JSON），只允许业务编号与脱敏字段。 */
    private String payloadJson;

    /** 去重键：sourceEventId + recipientAccountId + templateCode + channel。 */
    private String dedupeKey;

    /** 状态机，见 {@code NotificationTaskStatus}。 */
    private String status;

    private String priority;

    /** 已失败次数（成功重置无意义：任务一旦 SENT 即终态）。 */
    private Integer retryCount;

    private Integer maxRetryCount;

    /** 下次允许重试的时刻；null 表示无需等待退避。 */
    private LocalDateTime nextRetryAt;

    /** 最近一次失败原因（渲染失败 / 渠道异常），人工处理的入口信息。 */
    private String lastError;

    /** 允许开始发送的时刻（事件到达即入队，默认立即）。 */
    private LocalDateTime scheduledAt;

    private LocalDateTime sentAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
