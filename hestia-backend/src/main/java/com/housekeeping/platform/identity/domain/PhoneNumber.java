package com.housekeeping.platform.identity.domain;

/**
 * 手机号值对象。
 *
 * <p>规范（docs/modules/01 第 7.1 节）：</p>
 * <ul>
 *   <li>区号与号码分开保存，区号形如 {@code +86}；</li>
 *   <li>号码只保留数字，空格、横线、括号一律剥离；</li>
 *   <li>唯一索引建立在 {@code (phone_country_code, phone)} 上，
 *       因此"归一化"必须是<b>确定性</b>的——同一个手机号只能产生一种存储形态，
 *       否则 {@code 138-0013-8000} 和 {@code 13800138000} 会绕过唯一索引注册出两个账号。</li>
 * </ul>
 *
 * @param countryCode 区号，含前导 {@code +}
 * @param number      纯数字号码
 */
public record PhoneNumber(String countryCode, String number) {

    /** 中国大陆区号。 */
    public static final String CHINA = "+86";

    /** 大陆手机号规则：1 开头，第二位 3-9，共 11 位。 */
    private static final String CHINA_MOBILE_REGEX = "^1[3-9]\\d{9}$";

    /** 其他地区号码的宽松规则：5 至 15 位数字（E.164 上限 15 位）。 */
    private static final String GENERIC_NUMBER_REGEX = "^\\d{5,15}$";

    /**
     * 归一化并校验手机号。
     *
     * @param countryCode 区号，为空时按 {@code +86}
     * @param rawNumber   原始号码，允许含空格、横线、括号
     * @return 归一化后的手机号
     * @throws IllegalArgumentException 区号或号码格式非法
     */
    public static PhoneNumber parse(String countryCode, String rawNumber) {
        String normalizedCountryCode = normalizeCountryCode(countryCode);
        String digits = digitsOnly(rawNumber);
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        // 用户常把区号一起写进号码：+8613800138000 / 8613800138000，先剥离
        if (CHINA.equals(normalizedCountryCode) && digits.length() > 11 && digits.startsWith("86")) {
            digits = digits.substring(2);
        }
        String regex = CHINA.equals(normalizedCountryCode) ? CHINA_MOBILE_REGEX : GENERIC_NUMBER_REGEX;
        if (!digits.matches(regex)) {
            throw new IllegalArgumentException("手机号格式不正确");
        }
        return new PhoneNumber(normalizedCountryCode, digits);
    }

    /**
     * 归一化区号：补前导 {@code +}，只保留 {@code +} 与数字。
     *
     * @param countryCode 原始区号
     * @return 归一化区号
     */
    public static String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return CHINA;
        }
        String trimmed = countryCode.trim().replace(" ", "");
        String withPlus = trimmed.startsWith("+") ? trimmed : "+" + trimmed;
        if (!withPlus.matches("^\\+\\d{1,4}$")) {
            throw new IllegalArgumentException("国际区号格式不正确");
        }
        return withPlus;
    }

    /**
     * 提取纯数字。
     *
     * @param raw 原始字符串
     * @return 仅含数字的字符串；入参为空时返回空串
     */
    public static String digitsOnly(String raw) {
        return raw == null ? "" : raw.replaceAll("[^0-9]", "");
    }

    /**
     * 脱敏展示：保留前 3 位与后 4 位。
     *
     * <p>运营列表、日志、通知模板一律使用本方法，禁止直接拼接明文手机号。</p>
     *
     * @return 形如 {@code 138****8000}；号码过短时全部打码
     */
    public String masked() {
        if (number.length() < 8) {
            return "****";
        }
        return number.substring(0, 3) + "****" + number.substring(number.length() - 4);
    }

    /**
     * 完整号码（区号 + 号码），仅在必须提交给下游时才使用。
     *
     * @return 形如 {@code +8613800138000}
     */
    public String fullNumber() {
        return countryCode + number;
    }
}
