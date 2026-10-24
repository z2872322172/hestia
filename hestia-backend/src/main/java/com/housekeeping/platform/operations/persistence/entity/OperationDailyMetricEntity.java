package com.housekeeping.platform.operations.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 按日区域经营指标读模型，对应表 {@code operation_daily_metric}（docs/modules/13 第 14.3 节）。
 *
 * <p>由日报任务从各业务模块的公开统计契约聚合写入；(metric_date, service_area_id) 唯一，
 * 重复重算通过 Mapper 的 UPSERT 幂等完成；{@code version} 在本表语义为"重算次数"。</p>
 */
@Data
@TableName("operation_daily_metric")
public class OperationDailyMetricEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期（业务口径：订单创建日 / 事件发生日）。 */
    private LocalDate metricDate;

    /** 服务区域 ID，0 表示全公司汇总。 */
    private Long serviceAreaId;

    /** 订单创建数。 */
    private Integer ordersCreated;

    /** 支付成功订单数。 */
    private Integer ordersPaid;

    /** 完成订单数。 */
    private Integer ordersCompleted;

    /** 取消订单数。 */
    private Integer ordersCanceled;

    /** 改期次数。 */
    private Integer ordersRescheduled;

    /** 支付金额。 */
    private BigDecimal paidAmount;

    /** 退款金额。 */
    private BigDecimal refundAmount;

    /** 自动派单接单成功数。 */
    private Integer autoAssignmentSuccess;

    /** 拒单数。 */
    private Integer assignmentRejected;

    /** 接单超时数。 */
    private Integer assignmentTimeout;

    /** 人工派单数。 */
    private Integer manualAssignmentCount;

    /** 可用时段总数（全公司口径）。 */
    private Integer workerSlotTotal;

    /** 已占用时段数（全公司口径）。 */
    private Integer workerSlotBooked;

    /** 投诉 / 售后工单数。 */
    private Integer complaintCount;

    /** 返工工单数。 */
    private Integer reworkCount;

    /** 迟到次数（首版无数据源，保留列）。 */
    private Integer lateCount;

    /** 统计口径版本，口径变更时递增。 */
    private Integer metricVersion;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    /** 重算次数（乐观锁）。 */
    @Version
    private Long version;

    /** 本次计算时间。 */
    private LocalDateTime calculatedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
