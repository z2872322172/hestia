package com.housekeeping.platform.workforce.persistence.entity;

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
 * 服务人员业务档案，对应表 {@code worker_profile}。
 *
 * <p>与账号是 1:1：{@code account_id} 上有唯一索引。账号负责"能不能登录"，
 * 档案负责"能不能接活"，两者刻意分开——一个人可以被停用账号但保留档案（离职后查历史），
 * 也可以登录但暂时不可派单（健康证过期）。</p>
 *
 * <p>{@code rating} 与 {@code completedOrderCount} 是服务质量模块汇总后回写的展示摘要，
 * 事实来源是评价与完成订单事件。人员模块不自己算分，否则两处算分口径迟早不一致。</p>
 */
@Data
@TableName("worker_profile")
public class WorkerProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String workerNo;

    private Long accountId;

    private String realName;

    /** MALE / FEMALE / OTHER，可空。 */
    private String gender;

    /** FULL_TIME / PART_TIME。 */
    private String employmentType;

    /** ONBOARDING / ACTIVE / SUSPENDED / LEFT。 */
    private String workStatus;

    /** PENDING / APPROVED / REJECTED。 */
    private String auditStatus;

    private BigDecimal experienceYears;

    private String introduction;

    private Integer maxDailyOrders;

    private BigDecimal rating;

    private Integer completedOrderCount;

    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
