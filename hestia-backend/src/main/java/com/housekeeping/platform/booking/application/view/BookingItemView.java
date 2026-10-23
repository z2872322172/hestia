package com.housekeeping.platform.booking.application.view;

import java.math.BigDecimal;

/**
 * 订单金额明细行视图。
 *
 * @param itemType 构成类型（BASE/ADDON/MATERIAL/TRAVEL/DISCOUNT）
 * @param itemCode 明细编码，可空
 * @param itemName 名称快照
 * @param unitPrice 单价
 * @param quantity  数量
 * @param amount    金额
 */
public record BookingItemView(String itemType, String itemCode, String itemName,
                              BigDecimal unitPrice, BigDecimal quantity, BigDecimal amount) {
}
