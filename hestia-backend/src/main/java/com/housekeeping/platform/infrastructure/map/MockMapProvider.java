package com.housekeeping.platform.infrastructure.map;

/**
 * 本地 Mock 地图实现。
 *
 * <p>为什么需要它：没有外网或没有高德 Key 时，整个后端仍应能启动并跑通冒烟链路；
 * 否则每次开发都要依赖第三方服务可用性。</p>
 *
 * <p>坐标生成规则：以桂林市中心为基准，用地址文本的哈希做确定性偏移。
 * 同一地址永远得到同一坐标，便于测试断言，也不会让"服务区域判断"出现随机结果。</p>
 */
public class MockMapProvider implements MapProvider {

    /** 基准点：桂林市中心。 */
    private static final double BASE_LONGITUDE = 110.290D;

    private static final double BASE_LATITUDE = 25.274D;

    /** 偏移幅度约 0.05 度（约 5 公里），保证结果落在同一城市范围内。 */
    private static final double OFFSET_RANGE = 0.05D;

    private static final String PROVIDER_NAME = "mock";

    @Override
    public GeocodeResult geocode(String address) {
        int hash = address == null ? 0 : address.hashCode();
        double longitudeOffset = normalize(hash) * OFFSET_RANGE;
        double latitudeOffset = normalize(hash * 31) * OFFSET_RANGE;
        GeoPoint point = new GeoPoint(
                round(BASE_LONGITUDE + longitudeOffset),
                round(BASE_LATITUDE + latitudeOffset));
        return new GeocodeResult(PROVIDER_NAME, address, point, "MOCK");
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean healthy() {
        // Mock 实现没有外部依赖，永远可用
        return true;
    }

    /**
     * 把任意整数映射到 [-1, 1) 区间。
     */
    private double normalize(int value) {
        return (value % 1000) / 1000.0D;
    }

    private double round(double value) {
        return Math.round(value * 1_000_000D) / 1_000_000D;
    }
}
