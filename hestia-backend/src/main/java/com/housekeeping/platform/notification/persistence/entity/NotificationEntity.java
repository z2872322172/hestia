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
 * 站内通知，对应表 {@code notification}（docs/modules/12 第 12.4 节）。
 *
 * <p>一任务至多一条站内消息（{@code task_id} 唯一）：任务发送成功即落一条，
 * 用户已读只改 {@code read_at}，不物理删除，按保留策略归档。</p>
 */
@Data
@TableName("notification")
public class NotificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 通知业务编号，形如 NTF20261023000001。 */
    private String notificationNo;

    private Long taskId;

    private Long recipientAccountId;

    private String title;

    private String content;

    /** 业务类型（BOOKING / AFTER_SALE ...），前端跳转定位用。 */
    private String businessType;

    /** 业务编号（如订单号），存业务编号不存内部主键。 */
    private String businessId;

    private String priority;

    /** 已读时刻；null 即未读。 */
    private LocalDateTime readAt;

    /** 过期时刻（保留策略，可空）。 */
    private LocalDateTime expiresAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
