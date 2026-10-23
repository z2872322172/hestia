package com.housekeeping.platform.workforce.api.dto;

import jakarta.validation.Valid;

import java.util.List;

/**
 * 整体替换技能请求。
 *
 * @param skills 技能列表，空列表表示清空
 */
public record ReplaceSkillsRequest(@Valid List<SkillRequest> skills) {
}
