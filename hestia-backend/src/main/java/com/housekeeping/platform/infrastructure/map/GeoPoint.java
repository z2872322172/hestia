package com.housekeeping.platform.infrastructure.map;

/**
 * 经纬度坐标点。
 *
 * <p>使用 WGS84 以外的国内坐标系（GCJ-02）时，转换必须在地图适配器内完成，
 * 业务代码只认这一个坐标类型，避免同一个地址在不同模块出现不同坐标。</p>
 *
 * @param longitude 经度，-180 至 180
 * @param latitude  纬度，-90 至 90
 */
public record GeoPoint(double longitude, double latitude) {

    /**
     * 校验坐标合法性。
     *
     * @throws IllegalArgumentException 坐标越界
     */
    public GeoPoint {
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("经度超出合法范围：" + longitude);
        }
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("纬度超出合法范围：" + latitude);
        }
    }
}
