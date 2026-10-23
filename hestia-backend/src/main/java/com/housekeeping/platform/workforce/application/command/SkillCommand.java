package com.housekeeping.platform.workforce.application.command;

/**
 * 技能授权命令（运营端整体替换）。
 *
 * @param serviceItemNo 服务项目编号（业务编号，不是数据库 ID——运营手上只有编号）
 * @param skillLevel    技能等级
 * @param verified      是否直接审核通过
 */
public record SkillCommand(String serviceItemNo, String skillLevel, boolean verified) {
}
