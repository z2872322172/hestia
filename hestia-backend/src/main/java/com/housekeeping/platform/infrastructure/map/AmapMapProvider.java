package com.housekeeping.platform.infrastructure.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * 高德地图 Web 服务实现。
 *
 * <p>安全与稳定性要求：</p>
 * <ul>
 *   <li>Key 只从配置（环境变量）读取，缺失时构造阶段直接失败，避免运行期静默降级；</li>
 *   <li>必须设置连接/读取超时，外部接口慢不能拖住业务线程；</li>
 *   <li>日志只记录错误码和请求结果状态，<b>不记录地址原文</b>，防止门牌号进入日志。</li>
 * </ul>
 */
public class AmapMapProvider implements MapProvider {

    private static final Logger LOG = LoggerFactory.getLogger(AmapMapProvider.class);

    private static final String PROVIDER_NAME = "amap";

    /** 高德返回 status=1 表示成功。 */
    private static final String SUCCESS_STATUS = "1";

    private final MapProperties.Amap properties;

    private final ObjectMapper objectMapper;

    private final RestClient restClient;

    public AmapMapProvider(MapProperties.Amap properties, ObjectMapper objectMapper) {
        if (!StringUtils.hasText(properties.getWebServiceKey())) {
            throw new IllegalStateException(
                    "地图 provider 配置为 amap，但未提供 AMAP_WEB_SERVICE_KEY；"
                            + "请配置 Key 或改用 MAP_PROVIDER=mock");
        }
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = buildRestClient(properties);
    }

    @Override
    public GeocodeResult geocode(String address) {
        try {
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/geocode/geo")
                            .queryParam("key", properties.getWebServiceKey())
                            .queryParam("address", address)
                            .build())
                    .retrieve()
                    .body(String.class);
            return parse(responseBody, address);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            // 不打印地址，只记录异常类型
            LOG.warn("高德地理编码调用失败 type={}", exception.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "地图服务暂不可用", exception);
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean healthy() {
        try {
            // 用固定地标做探测，避免把用户地址写进健康检查
            String responseBody = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v3/geocode/geo")
                            .queryParam("key", properties.getWebServiceKey())
                            .queryParam("address", "桂林市")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(responseBody);
            return SUCCESS_STATUS.equals(root.path("status").asText());
        } catch (Exception exception) {
            LOG.warn("高德健康检查失败 type={}", exception.getClass().getSimpleName());
            return false;
        }
    }

    private GeocodeResult parse(String responseBody, String address) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        if (!SUCCESS_STATUS.equals(root.path("status").asText())) {
            String info = root.path("info").asText("unknown");
            LOG.warn("高德返回业务失败 infoCode={}", root.path("infocode").asText(""));
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "地图服务返回失败：" + info);
        }
        JsonNode geocodes = root.path("geocodes");
        if (!geocodes.isArray() || geocodes.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "地址无法解析为坐标");
        }
        JsonNode first = geocodes.get(0);
        String location = first.path("location").asText();
        String[] parts = location.split(",");
        if (parts.length != 2) {
            throw new BusinessException(ErrorCode.INTEGRATION_UNAVAILABLE, "地图服务返回坐标格式异常");
        }
        GeoPoint point = new GeoPoint(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]));
        return new GeocodeResult(PROVIDER_NAME,
                first.path("formatted_address").asText(address),
                point,
                first.path("level").asText("UNKNOWN"));
    }

    private RestClient buildRestClient(MapProperties.Amap amapProperties) {
        // 直接使用 JDK HttpClient：连接超时与读取超时都能显式设置，
        // 避免外部接口无响应时把业务线程拖死
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(amapProperties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(amapProperties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(amapProperties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
