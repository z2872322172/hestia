package com.housekeeping.platform.common.security;

/**
 * 当前操作账号的提供者（端口）。
 *
 * <p>审计字段填充、数据范围判断、审计日志都需要"当前登录账号"，但技术底座阶段还没有
 * identity 模块，因此先定义端口，由 {@code bootstrap} 层提供空实现。
 * 批次 1 引入 JWT 后，只需替换实现为从 SecurityContext 读取，调用方无需改动。</p>
 *
 * <p>返回值允许为 {@code null}：定时任务、Outbox Relay 等系统操作没有登录账号，
 * 此时审计字段留空，并通过 {@code operation_source = SYSTEM} 或 audit_log 记录来源。</p>
 */
public interface CurrentAccountProvider {

    /**
     * 获取当前请求的账号 ID。
     *
     * @return 账号 ID；系统任务或未登录时返回 {@code null}
     */
    Long currentAccountId();

    /**
     * 获取当前请求的活动角色编码。
     *
     * <p>活动角色来自 JWT 的 {@code role} 声明，是"这一次请求以什么身份操作"的唯一来源；
     * 需要判断数据范围（SELF / AREA / ALL）的模块必须读这里，不能读账号的全部角色。</p>
     *
     * @return 角色编码；未登录或系统任务返回 {@code null}
     */
    default String currentRoleCode() {
        return null;
    }
}
