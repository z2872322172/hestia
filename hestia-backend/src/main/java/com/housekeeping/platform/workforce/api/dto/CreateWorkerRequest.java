package com.housekeeping.platform.workforce.api.dto;

import com.housekeeping.platform.workforce.application.command.CreateWorkerCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建人员档案请求（运营端）。
 *
 * <p>手机号是必填的：人员必须能登录才能接单。只建档案不建账号，
 * 会得到一个"派得出去但没人能接"的档案。</p>
 *
 * @param countryCode     区号
 * @param phone           手机号
 * @param displayName     账号显示名
 * @param realName        真实姓名
 * @param gender          性别，可空
 * @param employmentType  用工类型：FULL_TIME / PART_TIME
 * @param experienceYears 从业年限
 * @param introduction    自我介绍
 * @param maxDailyOrders  每日最大接单量，1~20
 */
public record CreateWorkerRequest(
        @Size(max = 8, message = "区号过长") String countryCode,
        @NotBlank(message = "手机号不能为空") @Size(max = 20, message = "手机号过长") String phone,
        @Size(max = 64, message = "显示名过长") String displayName,
        @NotBlank(message = "真实姓名不能为空") @Size(max = 64, message = "真实姓名过长") String realName,
        @Size(max = 16, message = "性别过长") String gender,
        @NotBlank(message = "用工类型不能为空") String employmentType,
        @DecimalMin(value = "0", message = "从业年限不能为负")
        @DecimalMax(value = "60", message = "从业年限超出范围") BigDecimal experienceYears,
        @Size(max = 1000, message = "自我介绍过长") String introduction,
        @Min(value = 1, message = "每日最大接单量至少为 1")
        @Max(value = 20, message = "每日最大接单量最多为 20") Integer maxDailyOrders) {

    /**
     * 转应用层命令。
     *
     * @return 命令对象
     */
    public CreateWorkerCommand toCommand() {
        return new CreateWorkerCommand(countryCode, phone, displayName, realName, gender,
                employmentType, experienceYears, introduction, maxDailyOrders);
    }
}
