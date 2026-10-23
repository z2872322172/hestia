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
 * 人员排班记录，对应表 {@code worker_schedule}。
 *
 * <p>唯一键 {@code (worker_id, service_date, window_id)} 保证"一个人员同日同窗只有一条排班"，
 * 排班类型改变（工作改请假）是更新同一条记录而不是另插一行。</p>
 */
@Data
@TableName("worker_schedule")
public class WorkerScheduleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workerId;

    private LocalDate serviceDate;

    private Long windowId;

    /** WORK / LEAVE / TRAINING / REST。 */
    private String scheduleType;

    /** ACTIVE / CANCELED。 */
    private String status;

    /** WORKER / OPERATOR / SYSTEM。 */
    private String source;

    private String note;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
