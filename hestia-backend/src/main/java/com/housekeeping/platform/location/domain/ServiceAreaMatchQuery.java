package com.housekeeping.platform.location.domain;

/**
 * 地址匹配请求。
 *
 * <p>{@code cityCode} 与 {@code districtCode} 必填，{@code streetCode} 与坐标可选
 * （docs/modules/03 第 7.1 节）。坐标首版只落库不使用：服务区域按行政区匹配，
 * 不接地图多边形，避免本地开发依赖外部网络。</p>
 *
 * @param cityCode     城市行政区编码
 * @param districtCode 区县行政区编码
 * @param streetCode   街道行政区编码，可为 {@code null}
 * @param longitude    经度，可为 {@code null}
 * @param latitude     纬度，可为 {@code null}
 */
public record ServiceAreaMatchQuery(String cityCode, String districtCode, String streetCode,
                                    Double longitude, Double latitude) {
}
