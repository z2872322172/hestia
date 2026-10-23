package com.housekeeping.platform.customer.domain;

/**
 * 地址脱敏规则（docs/modules/02 第 16 节）。
 *
 * <p>展示级别按"谁在看"决定：</p>
 * <table>
 *   <tr><th>场景</th><th>联系人</th><th>电话</th><th>地址</th></tr>
 *   <tr><td>客户本人</td><td>完整</td><td>完整</td><td>完整</td></tr>
 *   <tr><td>运营列表</td><td>脱敏</td><td>脱敏</td><td>小区级</td></tr>
 *   <tr><td>服务人员接单前</td><td>脱敏</td><td>脱敏</td><td>区县级</td></tr>
 * </table>
 *
 * <p>脱敏必须在<b>服务端</b>完成：前端隐藏只能防君子，抓包即见明文。</p>
 */
public final class AddressMasking {

    private static final int KEEP_NAME_PREFIX = 1;

    private static final int KEEP_PHONE_PREFIX = 3;

    private static final int KEEP_PHONE_SUFFIX = 4;

    private AddressMasking() {
    }

    /**
     * 姓名脱敏：保留首字，其余打星。
     *
     * @param name 姓名
     * @return 脱敏结果，如 {@code 李*}
     */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.length() <= KEEP_NAME_PREFIX) {
            return "*";
        }
        return trimmed.substring(0, KEEP_NAME_PREFIX) + "*".repeat(trimmed.length() - KEEP_NAME_PREFIX);
    }

    /**
     * 电话脱敏：保留前 3 位与后 4 位。
     *
     * @param phone 纯数字电话
     * @return 脱敏结果，如 {@code 138****8000}
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < KEEP_PHONE_PREFIX + KEEP_PHONE_SUFFIX + 1) {
            return "****";
        }
        return phone.substring(0, KEEP_PHONE_PREFIX)
                + "****"
                + phone.substring(phone.length() - KEEP_PHONE_SUFFIX);
    }

    /**
     * 门牌脱敏：只保留到小区级别，楼栋单元门牌全部丢弃。
     *
     * <p>返回"区县 + 小区"而不是空字符串：派单前服务人员仍需要判断
     * "这单离我远不远"，只给区县已经够用，给门牌就构成隐私泄露。</p>
     *
     * @param districtName  区县名称
     * @param communityName 小区名称
     * @return 脱敏地址
     */
    public static String maskAddress(String districtName, String communityName) {
        String district = districtName == null ? "" : districtName;
        String community = communityName == null ? "" : communityName;
        return district + community;
    }
}
