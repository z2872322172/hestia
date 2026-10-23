package com.housekeeping.platform.customer.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户业务档案，对应表 {@code customer_profile}。
 *
 * <p>不重复保存手机号、密码和角色：账号属于认证域，客户属于业务域。
 * 两边都存手机号，改号时必然出现"账号已改、客户还是旧号"的不一致。</p>
 */
@Data
@TableName("customer_profile")
public class CustomerProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String customerNo;

    private Long accountId;

    /** ACTIVE / CLOSED。 */
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
