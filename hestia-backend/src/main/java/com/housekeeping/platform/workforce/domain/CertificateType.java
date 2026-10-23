package com.housekeeping.platform.workforce.domain;

/**
 * 证书类型（docs/modules/05 第 7.2 节）。
 *
 * <p>服务项目通过 {@code service_qualification_requirement} 声明需要哪些类型，
 * 人员的证书只要覆盖这些类型即可派单，不要求"某一张特定证书"。
 * 这样换证、补证都不需要改配置，只影响那一张证书的有效性。</p>
 */
public enum CertificateType {

    /** 身份证明。 */
    IDENTITY,

    /** 健康证。 */
    HEALTH,

    /** 背景核验。 */
    BACKGROUND_CHECK,

    /** 技能证书。 */
    SKILL_CERTIFICATE,

    /** 特种设备作业证（如空调清洗）。 */
    SPECIAL_EQUIPMENT,

    /** 其他。 */
    OTHER;

    /**
     * 解析证书类型。
     *
     * @param value 字符串
     * @return 枚举值
     * @throws IllegalArgumentException 取值非法
     */
    public static CertificateType of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("证书类型不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("证书类型非法：" + value);
        }
    }
}
