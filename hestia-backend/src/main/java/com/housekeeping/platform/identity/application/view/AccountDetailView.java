package com.housekeeping.platform.identity.application.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 后台账号详情。
 *
 * @param accountNo   账号编号
 * @param displayName 显示名称
 * @param phoneMasked 脱敏手机号
 * @param status      账号状态
 * @param roles       全部角色行（含暂停与撤销）
 * @param areaScopes  运营区域范围
 * @param lastLoginAt 最后登录时间
 */
public record AccountDetailView(String accountNo, String displayName, String phoneMasked, String status,
                                List<AccountRoleView> roles, List<AreaScopeView> areaScopes,
                                LocalDateTime lastLoginAt) {

    /**
     * 角色行视图。
     *
     * @param roleCode  角色编码
     * @param status    角色状态
     * @param grantedAt 授予时间
     */
    public record AccountRoleView(String roleCode, String status, LocalDateTime grantedAt) {
    }

    /**
     * 区域范围视图。
     *
     * @param roleCode      角色编码
     * @param serviceAreaId 服务区域 ID
     */
    public record AreaScopeView(String roleCode, Long serviceAreaId) {
    }
}
