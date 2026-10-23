package com.housekeeping.platform.scheduling.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 标准服务时间窗，对应表 {@code service_window}。
 *
 * <p>时间窗由公司统一配置（MORNING/AFTERNOON/EVENING…），是消费者可预约时间的展示骨架。
 * {@code window_code} 是稳定业务键：排班与时段都挂 {@code window_id}，
 * 但对外展示与冒烟脚本用编码，改名不破坏引用。</p>
 */
@Data
@TableName("service_window")
public class ServiceWindowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String windowCode;

    private String windowName;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer sortOrder;

    /** ACTIVE / DISABLED。 */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
