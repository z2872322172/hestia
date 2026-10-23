package com.housekeeping.platform.booking.api.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 冒烟支付确认请求（仅 /api/v1/system/smoke/** 开放）。
 *
 * @param paidAmount 实付金额
 */
public record SmokeMarkPaidRequest(@NotNull BigDecimal paidAmount) {
}
