package com.housekeeping.platform.operations.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.housekeeping.platform.operations.persistence.entity.OperationDailyMetricEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 按日区域经营指标 Mapper（docs/modules/13 第 8 节）。
 *
 * <p>写入口只有 UPSERT：日报任务重复执行时按 (metric_date, service_area_id)
 * 唯一键幂等覆盖，{@code version} 递增表示重算次数，历史口径可追溯。</p>
 */
@Mapper
public interface OperationDailyMetricMapper extends BaseMapper<OperationDailyMetricEntity> {

    /**
     * 幂等重算：存在同键行则覆盖指标列，否则新增。
     *
     * <p>覆盖不包含审计列（created_by / created_at 保持首次写入者）；
     * {@code version + 1} 与 {@code calculated_at = NOW(3)} 由 SQL 维护。</p>
     *
     * @param metric 指标行（须带 metricDate / serviceAreaId 与全部指标值）
     * @return 影响行数（1 新增 / 2 覆盖）
     */
    @Insert("INSERT INTO operation_daily_metric "
            + "(metric_date, service_area_id, orders_created, orders_paid, orders_completed, "
            + "orders_canceled, orders_rescheduled, paid_amount, refund_amount, "
            + "auto_assignment_success, assignment_rejected, assignment_timeout, manual_assignment_count, "
            + "worker_slot_total, worker_slot_booked, complaint_count, rework_count, late_count, "
            + "metric_version, calculated_at) "
            + "VALUES (#{metricDate}, #{serviceAreaId}, #{ordersCreated}, #{ordersPaid}, #{ordersCompleted}, "
            + "#{ordersCanceled}, #{ordersRescheduled}, #{paidAmount}, #{refundAmount}, "
            + "#{autoAssignmentSuccess}, #{assignmentRejected}, #{assignmentTimeout}, #{manualAssignmentCount}, "
            + "#{workerSlotTotal}, #{workerSlotBooked}, #{complaintCount}, #{reworkCount}, #{lateCount}, "
            + "#{metricVersion}, NOW(3)) "
            + "ON DUPLICATE KEY UPDATE "
            + "orders_created = VALUES(orders_created), orders_paid = VALUES(orders_paid), "
            + "orders_completed = VALUES(orders_completed), orders_canceled = VALUES(orders_canceled), "
            + "orders_rescheduled = VALUES(orders_rescheduled), paid_amount = VALUES(paid_amount), "
            + "refund_amount = VALUES(refund_amount), "
            + "auto_assignment_success = VALUES(auto_assignment_success), "
            + "assignment_rejected = VALUES(assignment_rejected), "
            + "assignment_timeout = VALUES(assignment_timeout), "
            + "manual_assignment_count = VALUES(manual_assignment_count), "
            + "worker_slot_total = VALUES(worker_slot_total), "
            + "worker_slot_booked = VALUES(worker_slot_booked), "
            + "complaint_count = VALUES(complaint_count), rework_count = VALUES(rework_count), "
            + "late_count = VALUES(late_count), metric_version = VALUES(metric_version), "
            + "version = version + 1, calculated_at = NOW(3)")
    int upsert(OperationDailyMetricEntity metric);
}
