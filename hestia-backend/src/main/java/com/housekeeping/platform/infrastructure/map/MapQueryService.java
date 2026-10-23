package com.housekeeping.platform.infrastructure.map;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 地图查询服务：业务侧唯一入口。
 *
 * <p>统一在这里做入参长度校验，防止超长地址被直接拼进第三方请求。</p>
 */
@Service
public class MapQueryService {

    /** 地址最大长度，与客户地址表字段长度保持一致。 */
    private static final int MAX_ADDRESS_LENGTH = 200;

    private final MapProvider mapProvider;

    public MapQueryService(MapProvider mapProvider) {
        this.mapProvider = mapProvider;
    }

    /**
     * 地址解析。
     *
     * @param address 地址文本
     * @return 地理编码结果
     */
    public GeocodeResult geocode(String address) {
        if (!StringUtils.hasText(address)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "地址不能为空");
        }
        String trimmed = address.trim();
        if (trimmed.length() > MAX_ADDRESS_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "地址长度不能超过 " + MAX_ADDRESS_LENGTH + " 个字符");
        }
        return mapProvider.geocode(trimmed);
    }

    /**
     * 当前生效的地图 Provider 名称。
     *
     * @return provider 名称
     */
    public String providerName() {
        return mapProvider.providerName();
    }
}
