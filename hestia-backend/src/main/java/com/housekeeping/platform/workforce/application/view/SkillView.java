package com.housekeeping.platform.workforce.application.view;

/**
 * 技能视图。
 *
 * <p>带上服务项目名称而不是只给 ID：运营在人员详情页要一眼看出"会做什么"，
 * 只给 {@code 1002} 等于让前端再查一次目录。</p>
 *
 * @param serviceItemNo   服务项目编号
 * @param serviceItemName 服务项目名称
 * @param skillLevel      技能等级
 * @param verified        是否已审核
 * @param status          ACTIVE / DISABLED
 * @param verifiedAt      审核时间
 */
public record SkillView(String serviceItemNo,
                        String serviceItemName,
                        String skillLevel,
                        boolean verified,
                        String status,
                        java.time.LocalDateTime verifiedAt) {
}
