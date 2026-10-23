package com.housekeeping.platform.location.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 服务区域匹配请求。
 *
 * @param cityCode     城市行政区编码，必填
 * @param districtCode 区县行政区编码，必填
 * @param streetCode   街道行政区编码，可选
 * @param longitude    经度，可选（首版只落库不参与匹配）
 * @param latitude     纬度，可选
 */
public record ServiceAreaMatchRequest(
        @NotBlank(message = "城市编码不能为空") @Size(max = 12, message = "城市编码过长") String cityCode,
        @NotBlank(message = "区县编码不能为空") @Size(max = 12, message = "区县编码过长") String districtCode,
        @Size(max = 12, message = "街道编码过长") String streetCode,
        @DecimalMin(value = "-180", message = "经度超出范围") @DecimalMax(value = "180", message = "经度超出范围") Double longitude,
        @DecimalMin(value = "-90", message = "纬度超出范围") @DecimalMax(value = "90", message = "纬度超出范围") Double latitude) {
}
