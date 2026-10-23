package com.housekeeping.platform.identity.application.view;

import java.util.List;

/**
 * 账号视图。
 *
 * <p>手机号只给脱敏值；角色只给编码不给权限明细；
 * 密码哈希、令牌版本、失败次数等安全字段一律不出接口。</p>
 *
 * @param accountNo   账号编号
 * @param displayName 显示名称
 * @param activeRole  当前活动角色
 * @param status      账号状态
 * @param phoneMasked 脱敏手机号
 * @param roles       已授予且有效的角色编码
 */
public record AccountView(String accountNo, String displayName, String activeRole,
                          String status, String phoneMasked, List<String> roles) {
}
