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
 * 订单评价，对应表 {@code service_review}（docs/modules/11 第 11.2 节）。
 *
 * <p>唯一键 {@code booking_id}：一单最多一条评价；隐藏不删除，
 * 保留原始内容与操作审计（docs 第 2.11 条）。</p>
 */
@Data
@TableName("service_review")
public class ServiceReviewEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String reviewNo;

    private Long bookingId;

    private Long customerId;

    private Long workerId;

    /** 综合分（各维度简单平均，0-5）。 */
    private BigDecimal overallScore;

    private String content;

    /** VISIBLE / HIDDEN。 */
    private String status;

    private String hiddenReason;

    private Long hiddenByAccountId;

    private LocalDateTime hiddenAt;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
