package com.housekeeping.platform.booking.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单金额明细行，对应表 {@code booking_item}（docs/modules/07 第 15.2 节）。
 *
 * <p>每个金额构成（BASE/ADDON/MATERIAL/TRAVEL/DISCOUNT）落一行，
 * 报价单据可追溯；只记录金额大于零的构成。</p>
 */
@Data
@TableName("booking_item")
public class BookingItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long bookingId;

    /** BASE / ADDON / MATERIAL / TRAVEL / DISCOUNT / ADJUSTMENT。 */
    private String itemType;

    private String itemCode;

    private String itemNameSnapshot;

    private BigDecimal unitPriceSnapshot;

    private BigDecimal quantity;

    private BigDecimal amount;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private Long createdByAccountId;

    private LocalDateTime createdAt;
}
