package com.housekeeping.platform.workforce.api.dto;

import com.housekeeping.platform.workforce.application.command.UpdateWorkerCommand;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 修改人员档案请求。
 *
 * @param realName        真实姓名，空表示不改
 * @param gender          性别
 * @param employmentType  用工类型
 * @param experienceYears 从业年限
 * @param introduction    自我介绍
 * @param maxDailyOrders  每日最大接单量
 * @param version         客户端读到的版本号，必填
 */
public record UpdateWorkerRequest(
        @Size(max = 64, message = "真实姓名过长") String realName,
        @Size(max = 16, message = "性别过长") String gender,
        String employmentType,
        @DecimalMin(value = "0", message = "从业年限不能为负")
        @DecimalMax(value = "60", message = "从业年限超出范围") BigDecimal experienceYears,
        @Size(max = 1000, message = "自我介绍过长") String introduction,
        @Min(value = 1, message = "每日最大接单量至少为 1")
        @Max(value = 20, message = "每日最大接单量最多为 20") Integer maxDailyOrders,
        @NotNull(message = "版本号不能为空") Long version) {

    /**
     * 转应用层命令。
     *
     * @return 命令对象
     */
    public UpdateWorkerCommand toCommand() {
        return new UpdateWorkerCommand(realName, gender, employmentType, experienceYears,
                introduction, maxDailyOrders, version);
    }
}
