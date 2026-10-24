package com.housekeeping.platform.notification.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知渠道发送记录，对应表 {@code notification_delivery}（docs/modules/12 第 12.3 节）。
 *
 * <p>只追加表：每次发送尝试一行，(task_id, channel, attempt_no) 唯一，
 * 重试的 attempt_no 递增，问题排查时能看到完整尝试历史。
 * 无审计人字段、无乐观锁——记录一旦写入不再修改。</p>
 */
@Data
@TableName("notification_delivery")
public class NotificationDeliveryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** 发送渠道。 */
    private String channel;

    /** 第几次尝试（从 1 开始，与任务 retry_count + 1 对应）。 */
    private Integer attemptNo;

    /** SENDING / SENT / FAILED / SKIPPED。 */
    private String status;

    /** 渠道方消息 ID（站内渠道无外部网关，可空）。 */
    private String providerMessageId;

    /** 失败原因。 */
    private String errorMessage;

    private LocalDateTime sentAt;

    private LocalDateTime createdAt;
}
