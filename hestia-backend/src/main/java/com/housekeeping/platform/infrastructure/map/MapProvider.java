package com.housekeeping.platform.infrastructure.map;

/**
 * 地图服务端口。
 *
 * <p>业务代码不得直接调用高德 HTTP 接口或 SDK：地图供应商可替换，
 * 且外部接口的限流、超时和失败重试策略必须集中管理。</p>
 */
public interface MapProvider {

    /**
     * 地址解析为坐标。
     *
     * @param address 结构化地址文本
     * @return 地理编码结果
     * @throws com.housekeeping.platform.common.exception.BusinessException 地址无法解析或外部服务不可用
     */
    GeocodeResult geocode(String address);

    /**
     * 提供者名称，用于日志与健康检查。
     *
     * @return 如 {@code mock} 或 {@code amap}
     */
    String providerName();

    /**
     * 健康检查。
     *
     * @return 可用返回 {@code true}
     */
    boolean healthy();
}
