package com.housekeeping.platform.workforce.domain;

import java.util.List;

/**
 * 资格判定结果。
 *
 * <p>{@code eligible} 与 {@code reasons} 是一对<b>互斥且完备</b>的字段：
 * 可派单时原因列表必为空，不可派单时原因列表必非空。服务层不要"用 reasons 是否为空
 * 反推 eligible"，两个字段都写出来是为了让接口响应自解释。</p>
 *
 * @param eligible 是否可派单
 * @param reasons  不可派单的原因，按 {@link EligibilityReason} 声明顺序排列
 */
public record EligibilityResult(boolean eligible, List<EligibilityReason> reasons) {

    /**
     * 构造可派单结果。
     *
     * @return 可派单
     */
    public static EligibilityResult pass() {
        return new EligibilityResult(true, List.of());
    }

    /**
     * 构造不可派单结果。
     *
     * @param reasons 原因列表，不能为空
     * @return 不可派单
     */
    public static EligibilityResult fail(List<EligibilityReason> reasons) {
        if (reasons == null || reasons.isEmpty()) {
            throw new IllegalArgumentException("不可派单必须给出至少一个原因");
        }
        return new EligibilityResult(false, List.copyOf(reasons));
    }

    /**
     * 原因编码列表（用于接口响应）。
     *
     * @return 原因编码
     */
    public List<String> reasonCodes() {
        return reasons.stream().map(Enum::name).toList();
    }
}
