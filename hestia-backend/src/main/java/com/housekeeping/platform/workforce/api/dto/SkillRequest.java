package com.housekeeping.platform.workforce.api.dto;

import com.housekeeping.platform.workforce.application.command.SkillCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 技能授权请求项。
 *
 * @param serviceItemNo 服务项目编号
 * @param skillLevel    技能等级：JUNIOR / INTERMEDIATE / SENIOR
 * @param verified      是否直接审核通过
 */
public record SkillRequest(
        @NotBlank(message = "服务项目编号不能为空") @Size(max = 32, message = "服务项目编号过长")
        String serviceItemNo,
        @NotBlank(message = "技能等级不能为空") String skillLevel,
        boolean verified) {

    /**
     * 转应用层命令。
     *
     * @return 命令对象
     */
    public SkillCommand toCommand() {
        return new SkillCommand(serviceItemNo, skillLevel, verified);
    }
}
