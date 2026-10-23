package com.housekeeping.platform.booking.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单主状态日志，对应表 {@code booking_status_log}（docs/modules/07 第 15.3 节）。
 *
 * <p>只追加不修改；与订单状态变化同事务写入（docs 第 17 节）。</p>
 */
@Data
@TableName("booking_status_log")
public class BookingStatusLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookingId;

    /** 迁移前状态，首条日志为 NULL。 */
    private String fromStatus;

    private String toStatus;

    /** 操作人账号 ID，系统操作为 NULL。 */
    private Long operatorId;

    private String operatorRole;

    /** USER / WORKER / OPERATOR / SYSTEM。 */
    private String operationSource;

    private String reasonCode;

    private String reasonText;

    private String traceId;

    private LocalDateTime createdAt;
}
