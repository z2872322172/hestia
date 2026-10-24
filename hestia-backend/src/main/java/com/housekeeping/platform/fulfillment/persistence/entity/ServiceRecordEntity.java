package com.housekeeping.platform.fulfillment.persistence.entity;

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
 * 服务履约记录，对应表 {@code service_record}（docs/modules/09 第 14.1 节）。
 *
 * <p>唯一键 {@code booking_id}：一条订单最多一条履约记录；改派时原记录重置回
 * PENDING 并换绑新人员（docs 第 18.2 节：改派后旧履约不能继续推进）。</p>
 */
@Data
@TableName("service_record")
public class ServiceRecordEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String recordNo;

    private Long bookingId;

    private Long workerId;

    /** PENDING / EN_ROUTE / CHECKED_IN / IN_SERVICE / PENDING_CONFIRM / COMPLETED / EXCEPTION / CANCELED。 */
    private String status;

    private LocalDateTime departedAt;

    private LocalDateTime checkInAt;

    private BigDecimal checkInLongitude;

    private BigDecimal checkInLatitude;

    /** GPS / MANUAL / SYSTEM。 */
    private String checkInSource;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime completionRequestedAt;

    private LocalDateTime confirmationDeadline;

    private LocalDateTime customerConfirmedAt;

    /** CUSTOMER / SYSTEM / OPERATOR。 */
    private String confirmSource;

    private String serviceResult;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updatedByAccountId;

    @Version
    private Long version;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
