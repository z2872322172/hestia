package com.housekeeping.platform.location.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增覆盖范围请求。
 *
 * @param regionCode 行政区编码（区县或街道）
 */
public record AddCoverageRequest(
        @NotBlank(message = "行政区编码不能为空") @Size(max = 12, message = "行政区编码过长") String regionCode) {
}
