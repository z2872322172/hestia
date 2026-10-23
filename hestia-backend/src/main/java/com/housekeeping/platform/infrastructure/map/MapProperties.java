package com.housekeeping.platform.infrastructure.map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 地图服务配置。
 *
 * <p>业务代码只依赖 {@link MapProvider}，Provider 名称由配置切换：
 * {@code mock} 可在无外网环境启动，{@code amap} 需要配置 Web 服务 Key。</p>
 */
@Data
@ConfigurationProperties(prefix = "app.map")
public class MapProperties {

    /** provider 名称：mock 或 amap。 */
    private String provider = "mock";

    private Amap amap = new Amap();

    /**
     * 高德开放平台配置。
     */
    @Data
    public static class Amap {

        /** Web 服务 Key，必须来自环境变量。 */
        private String webServiceKey;

        private String baseUrl = "https://restapi.amap.com";

        /** 连接超时：外部服务不可达时必须快速失败，不能拖住请求线程。 */
        private Duration connectTimeout = Duration.ofSeconds(2);

        private Duration readTimeout = Duration.ofSeconds(3);
    }
}
