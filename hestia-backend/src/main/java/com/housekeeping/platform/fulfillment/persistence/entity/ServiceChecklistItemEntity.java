package com.housekeeping.platform.fulfillment.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 服务检查项快照与执行结果，对应表 {@code service_checklist_item}
 * （docs/modules/09 第 14.2 节）。
 *
 * <p>内容来自下单时刻的 {@code booking_order.service_standard_snapshot_json}，
 * 目录模板后续变化不影响已完成订单的验收标准（docs 第 8 节）。</p>
 */
@Data
@TableName("service_checklist_item")
public class ServiceChecklistItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long serviceRecordId;

    private Long sourceStandardItemId;

    private String standardGroup;

    private String itemName;

    private String description;

    private Boolean required;

    private Boolean evidenceRequired;

    private Boolean completed;

    private LocalDateTime completedAt;

    private Long completedByAccountId;

    private String remark;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
