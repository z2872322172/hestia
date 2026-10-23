package com.housekeeping.platform.workforce.application.view;

/**
 * 服务项目资质要求视图。
 *
 * @param certificateType 证书类型
 * @param required        是否必需（{@code false} 表示"有则优先"，不阻断派单）
 */
public record QualificationRequirementView(String certificateType, boolean required) {
}
