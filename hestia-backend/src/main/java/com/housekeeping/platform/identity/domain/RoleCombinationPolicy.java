package com.housekeeping.platform.identity.domain;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * 角色组合规则（docs/modules/01 第 4.2 节）。
 *
 * <p>允许：CUSTOMER、WORKER、CUSTOMER + WORKER、OPERATOR、ADMIN。</p>
 * <p>禁止：任何"外部 + 内部"的组合。</p>
 *
 * <p>为什么必须禁止混用：内部角色拥有跨客户的数据访问能力，
 * 一旦与消费者身份共存于同一账号，客服页面的一次误操作就可能把内部视角
 * 带进消费者侧接口（例如用同一 Token 调用运营接口），越权面被放大到无法审计。</p>
 */
public final class RoleCombinationPolicy {

    private RoleCombinationPolicy() {
    }

    /**
     * 校验角色组合是否合法。
     *
     * @param roles 目标角色集合
     * @return 校验通过返回 {@code true}
     */
    public static boolean isAllowed(Collection<RoleCode> roles) {
        if (roles == null || roles.isEmpty()) {
            return false;
        }
        Set<RoleCode> distinct = EnumSet.copyOf(roles);
        boolean hasExternal = distinct.stream().anyMatch(role -> !role.isInternal());
        boolean hasInternal = distinct.stream().anyMatch(RoleCode::isInternal);
        return !(hasExternal && hasInternal);
    }

    /**
     * 校验并返回规范化后的角色集合。
     *
     * @param roles 目标角色集合
     * @return 不可变角色集合
     * @throws IllegalArgumentException 组合非法或为空
     */
    public static Set<RoleCode> requireAllowed(Collection<RoleCode> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("至少需要分配一个角色");
        }
        Set<RoleCode> distinct = EnumSet.copyOf(roles);
        if (!isAllowed(distinct)) {
            throw new IllegalArgumentException("内部角色与外部角色不能分配给同一账号");
        }
        return Set.copyOf(distinct);
    }
}
