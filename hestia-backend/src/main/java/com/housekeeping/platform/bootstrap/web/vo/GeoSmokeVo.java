package com.housekeeping.platform.bootstrap.web.vo;

/**
 * 地理编码冒烟响应。
 *
 * @param provider         实际使用的 Provider
 * @param formattedAddress 标准化地址
 * @param longitude        经度
 * @param latitude         纬度
 * @param level            匹配级别
 */
public record GeoSmokeVo(String provider, String formattedAddress,
                         double longitude, double latitude, String level) {
}
