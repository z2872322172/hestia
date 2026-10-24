package com.housekeeping.platform.fulfillment.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 履约异常，对应表 {@code service_exception}（docs/modules/09 第 14.4 节）。
 *
 * <p>异常不直接修改订单金额（docs 第 10 节）；现场加项走异常申请由运营处理。</p>
 */
@Data
@TableName("service_exception")
public class ServiceExceptionEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String exceptionNo;

    private Long serviceRecordId;

    /** CUSTOMER_ABSENT / ADDRESS_INCORRECT / ACCESS_DENIED / SAFETY_RISK / MATERIAL_MISSING / EXTRA_SERVICE / OTHER。 */
    private String exceptionType;

    private String description;

    /** OPEN / ACKNOWLEDGED / RESOLVED / REJECTED / CLOSED。 */
    private String status;

    private Long reportedByAccountId;

    private Long handledByAccountId;

    private String resolution;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime reportedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
