package com.housekeeping.platform.booking.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单改期历史，对应表 {@code booking_schedule_history}（docs/modules/07 第 15.4 节）。
 *
 * <p>一次改期写一行，记录新旧时段与新旧人员，只追加不修改。</p>
 */
@Data
@TableName("booking_schedule_history")
public class BookingScheduleHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookingId;

    private LocalDate oldAppointmentDate;

    private Long oldServiceWindowId;

    private Long oldWorkerSlotId;

    private Long oldWorkerId;

    private LocalDate newAppointmentDate;

    private Long newServiceWindowId;

    private Long newWorkerSlotId;

    private Long newWorkerId;

    private String reasonCode;

    private String reasonText;

    private Long operatorId;

    private LocalDateTime createdAt;
}
