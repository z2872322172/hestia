package com.housekeeping.platform.customer.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 地址标签（docs/modules/02 第 6.2 节）。
 *
 * <p>标签只用于展示，<b>不是</b>权限或计价依据。
 * 一旦有人用"OFFICE"来决定服务费，标签就会立刻变成业务规则，届时必须走正式的状态字段。</p>
 */
public enum AddressLabel {

    HOME,

    PARENTS,

    OFFICE,

    RENTAL,

    OTHER;

    /**
     * 解析标签，非法值回退为 {@link #OTHER}。
     *
     * <p>标签是展示信息，不值得因为客户端传了未知标签而拒绝整个地址保存请求。</p>
     *
     * @param label 标签字符串
     * @return 标签，永不返回 {@code null}
     */
    public static AddressLabel ofOrDefault(String label) {
        if (label == null || label.isBlank()) {
            return HOME;
        }
        return Arrays.stream(values())
                .filter(value -> value.name().equalsIgnoreCase(label.trim()))
                .findFirst()
                .orElse(OTHER);
    }

    /**
     * 解析标签。
     *
     * @param label 标签字符串
     * @return 标签；非法时为空
     */
    public static Optional<AddressLabel> of(String label) {
        return Arrays.stream(values()).filter(value -> value.name().equals(label)).findFirst();
    }
}
