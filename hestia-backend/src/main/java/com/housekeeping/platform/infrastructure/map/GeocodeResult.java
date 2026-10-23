package com.housekeeping.platform.infrastructure.map;

/**
 * 地理编码结果。
 *
 * @param provider         实际提供结果的适配器名称
 * @param formattedAddress 标准化地址；日志中不得输出该字段
 * @param point            坐标
 * @param level            匹配级别（如"门牌号""小区"），Mock 实现固定返回 {@code MOCK}
 */
public record GeocodeResult(String provider, String formattedAddress, GeoPoint point, String level) {
}
