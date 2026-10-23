package com.housekeeping.platform.identity.domain;

/**
 * 密码策略（docs/modules/01 第 7.1 节）。
 *
 * <p>规则刻意保持"可解释、可提示"：长度 8 至 64、必须同时包含字母和数字、不得与手机号相同。
 * 不引入"必须含特殊字符""90 天强制改密"这类规则——前者对真实安全性收益很低，
 * 后者会把密码推向 {@code Password1!} 这种可预测形态。</p>
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;

    public static final int MAX_LENGTH = 64;

    private PasswordPolicy() {
    }

    /**
     * 校验密码强度。
     *
     * @param rawPassword 明文密码
     * @param phone       账号手机号，用于"密码不得等于手机号"的判断，可为 {@code null}
     * @throws IllegalArgumentException 不符合策略，异常信息可直接展示给用户
     */
    public static void validate(String rawPassword, PhoneNumber phone) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (rawPassword.length() < MIN_LENGTH || rawPassword.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("密码长度需为 " + MIN_LENGTH + " 至 " + MAX_LENGTH + " 位");
        }
        boolean hasLetter = rawPassword.chars().anyMatch(Character::isLetter);
        boolean hasDigit = rawPassword.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("密码需同时包含字母和数字");
        }
        if (phone != null && rawPassword.contains(phone.number())) {
            throw new IllegalArgumentException("密码不能包含手机号");
        }
    }
}
