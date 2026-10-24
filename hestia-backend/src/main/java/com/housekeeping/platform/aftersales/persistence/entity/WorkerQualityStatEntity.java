package com.housekeeping.platform.aftersales.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 人员质量汇总，对应表 {@code worker_quality_stat}（docs/modules/11 第 11.8 节）。
 *
 * <p>只供派单评分与运营查看，可全量重算，不作为资金或订单事实来源
 * （docs 第 2.9、10 节）。事件驱动更新 + 定时任务兜底。</p>
 */
@Data
@TableName("worker_quality_stat")
public class WorkerQualityStatEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workerId;

    /** 可见评价数。 */
    private Integer reviewCount;

    /** 平均综合分（0-5，可见评价）。 */
    private BigDecimal averageRating;

    /** 已完成订单数。 */
    private Integer completedOrderCount;

    /** 投诉工单数（未驳回的质量类工单）。 */
    private Integer complaintCount;

    /** 迟到工单数。 */
    private Integer lateCount;

    /** 返工工单数。 */
    private Integer reworkCount;

    private LocalDateTime lastCalculatedAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
