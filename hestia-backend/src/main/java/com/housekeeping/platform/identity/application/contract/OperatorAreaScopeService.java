package com.housekeeping.platform.identity.application.contract;

/**
 * 运营数据范围解析契约（docs/modules/13 第 6 节）。
 *
 * <p>运营模块及各模块运营侧查询都需要"这个账号能看哪些区域"。
 * 区域范围只存放在 identity 模块（{@code account_area_scope}），其他模块
 * 通过本契约读取，禁止直接查该表。</p>
 *
 * <p>范围判定规则：活动角色为 ADMIN 返回全量；OPERATOR 返回其
 * {@code account_area_scope} 中配置的区域（可能为空 = 看不到数据）；
 * 其他角色没有运营数据范围，返回空限定范围。</p>
 */
public interface OperatorAreaScopeService {

    /**
     * 解析账号在指定角色下的运营数据范围。
     *
     * <p>必须传"当前请求的活动角色"（{@code CurrentAccountProvider#currentRoleCode()}），
     * 不能传账号的全部角色：数据范围跟着本次请求的身份走。</p>
     *
     * @param accountId 账号 ID
     * @param roleCode  活动角色编码（如 OPERATOR / ADMIN）
     * @return 数据范围；非运营角色返回空限定范围
     */
    AreaScope resolveAreaScope(long accountId, String roleCode);
}
