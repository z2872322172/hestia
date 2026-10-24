package com.housekeeping.platform.notification.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.housekeeping.platform.notification.domain.NotificationAudience;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知模板，对应表 {@code notification_template}（docs/modules/12 第 12.1 节）。
 *
 * <p>主键是模板编码（业务键）而非自增 ID。{@code version} 是<b>内容版本</b>：
 * 运营每次修改标题/正文自增 1，用于追溯"这条通知当时用的哪版文案"；
 * 它不是乐观锁——模板只有运营单点维护，冲突概率可忽略。</p>
 */
@Data
@TableName("notification_template")
public class NotificationTemplateEntity {

    /** 模板编码，形如 NTPL_BKG_PAID_CUS。 */
    @TableId
    private String templateCode;

    private String templateName;

    /** 触发事件类型，见 {@code NotificationEventTypes}。 */
    private String eventType;

    /** 受众（模板选择键），见 {@link NotificationAudience}。 */
    private String audience;

    /** 发送渠道，首版只有 IN_APP。 */
    private String channel;

    private String titleTemplate;

    private String contentTemplate;

    /** LOW / NORMAL / HIGH / URGENT，透传给任务与站内消息。 */
    private String priority;

    /** ACTIVE / DISABLED。 */
    private String status;

    /** 内容版本：每次修改 +1，从 1 开始。 */
    private Long version;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
