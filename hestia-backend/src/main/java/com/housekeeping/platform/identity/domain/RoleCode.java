package com.housekeeping.platform.identity.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 角色编码（docs/modules/01 第 4.1 节）。
 *
 * <p>角色是固定的四类业务身份，不允许运营人员动态新增：
 * 角色决定了数据范围（SELF / AREA / ALL），动态角色会让数据权限变成不可推理的配置。</p>
 */
public enum RoleCode {

    /** 消费者：只能访问自己的客户数据。 */
    CUSTOMER(false),

    /** 服务人员：只能访问自己的档案与被分配的订单。 */
    WORKER(false),

    /** 运营人员：按服务区域访问运营数据。 */
    OPERATOR(true),

    /** 系统管理员：可访问全部后台数据。 */
    ADMIN(true);

    private final boolean internal;

    RoleCode(boolean internal) {
        this.internal = internal;
    }

    /**
     * 是否为内部账号角色。
     *
     * @return 内部角色返回 {@code true}
     */
    public boolean isInternal() {
        return internal;
    }

    /**
     * 按编码解析角色。
     *
     * @param code 角色编码
     * @return 角色；编码非法时为空
     */
    public static Optional<RoleCode> of(String code) {
        return Arrays.stream(values()).filter(role -> role.name().equals(code)).findFirst();
    }
}
