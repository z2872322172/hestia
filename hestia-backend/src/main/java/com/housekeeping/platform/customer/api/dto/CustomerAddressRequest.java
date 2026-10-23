package com.housekeeping.platform.customer.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 新增/修改地址请求。
 *
 * @param label              标签
 * @param contactName        联系人
 * @param contactCountryCode 联系人区号
 * @param contactPhone       联系电话
 * @param provinceCode       省编码
 * @param cityCode           市编码（服务端以行政区字典为准，此处仅作参考）
 * @param districtCode       区县编码
 * @param streetCode         街道编码
 * @param communityName      小区
 * @param buildingNo         楼栋
 * @param unitNo             单元
 * @param floorNo            楼层
 * @param roomNo             门牌
 * @param detailAddress      详细地址
 * @param longitude          经度
 * @param latitude           纬度
 * @param accessNote         履约说明
 * @param isDefault          是否设为默认（仅新增生效）
 * @param version            修改时的版本号
 */
public record CustomerAddressRequest(
        @Size(max = 32, message = "标签过长") String label,
        @NotBlank(message = "联系人不能为空") @Size(max = 64, message = "联系人过长") String contactName,
        @Size(max = 8, message = "区号过长") String contactCountryCode,
        @NotBlank(message = "联系电话不能为空") @Size(max = 20, message = "联系电话过长") String contactPhone,
        @Size(max = 12, message = "省编码过长") String provinceCode,
        @Size(max = 12, message = "市编码过长") String cityCode,
        @NotBlank(message = "区县编码不能为空") @Size(max = 12, message = "区县编码过长") String districtCode,
        @Size(max = 12, message = "街道编码过长") String streetCode,
        @Size(max = 128, message = "小区名称过长") String communityName,
        @Size(max = 64, message = "楼栋过长") String buildingNo,
        @Size(max = 32, message = "单元过长") String unitNo,
        @Size(max = 16, message = "楼层过长") String floorNo,
        @Size(max = 32, message = "门牌过长") String roomNo,
        @NotBlank(message = "详细地址不能为空") @Size(max = 255, message = "详细地址过长") String detailAddress,
        @DecimalMin(value = "-180", message = "经度超出范围") @DecimalMax(value = "180", message = "经度超出范围") BigDecimal longitude,
        @DecimalMin(value = "-90", message = "纬度超出范围") @DecimalMax(value = "90", message = "纬度超出范围") BigDecimal latitude,
        @Size(max = 500, message = "履约说明过长") String accessNote,
        Boolean isDefault,
        Long version) {
}
