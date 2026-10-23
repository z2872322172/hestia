package com.housekeeping.platform.scheduling.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人员时段库存，对应表 {@code worker_slot}。
 *
 * <p>这是排班生成的可锁定资源：下单锁时段（AVAILABLE→HELD）、支付确认（HELD→BOOKED）、
 * 超时/取消释放（HELD/BOOKED→AVAILABLE）全部通过 {@code WorkerSlotMapper} 的条件更新完成，
 * 本实体只做读取与生成，禁止用 {@code updateById} 改状态——它会绕过条件更新直接覆盖，
 * 并发下会把别人刚锁的时段写坏。</p>
 *
 * <p>{@code bookingId} 是订单模块的内部主键，逻辑外键不建级联（docs/modules/06 第 13 节）。</p>
 */
@Data
@TableName("worker_slot")
public class WorkerSlotEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workerId;

    private Long scheduleId;

    private LocalDate serviceDate;

    private Long windowId;

    /** AVAILABLE / HELD / BOOKED / BLOCKED / CLOSED。 */
    private String status;

    private Long bookingId;

    private LocalDateTime holdExpiresAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
