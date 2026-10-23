package com.housekeeping.platform.workforce.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人员服务区域，对应表 {@code worker_service_area}。
 *
 * <p>停用而不是删除：人员从一个片区调走，历史订单还挂在这个片区上，
 * 删掉关系会让"当时谁负责这个片区"变成查不出来的事。</p>
 */
@Data
@TableName("worker_service_area")
public class WorkerServiceAreaEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long workerId;

    private Long serviceAreaId;

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
