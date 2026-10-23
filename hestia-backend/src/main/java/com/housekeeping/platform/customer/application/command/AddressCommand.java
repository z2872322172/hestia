package com.housekeeping.platform.customer.application.command;

import java.math.BigDecimal;

/**
 * 地址保存命令（新增与修改共用）。
 *
 * <p>刻意<b>不含</b> {@code status}：是否删除走独立接口。
 * 让保存接口顺带改状态，等于把状态机的入口开在参数里。</p>
 *
 * <p>{@code isDefault} 只在<b>新增</b>时生效（对应 docs/modules/02 第 14.4 节的请求示例），
 * 修改地址时一律忽略——否则"改个门牌顺手把默认地址换掉"这类操作无法追溯，
 * 切换默认地址必须走 {@code PUT /addresses/{addressNo}/default}。</p>
 *
 * @param label              标签
 * @param contactName        联系人
 * @param contactCountryCode 联系人区号
 * @param contactPhone       联系电话
 * @param provinceCode       省编码
 * @param cityCode           市编码，为空时由区县反推
 * @param districtCode       区县编码，必填
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
 * @param version            修改时的版本号，新增时为 {@code null}
 */
public record AddressCommand(String label, String contactName, String contactCountryCode, String contactPhone,
                             String provinceCode, String cityCode, String districtCode, String streetCode,
                             String communityName, String buildingNo, String unitNo, String floorNo,
                             String roomNo, String detailAddress, BigDecimal longitude, BigDecimal latitude,
                             String accessNote, Boolean isDefault, Long version) {
}
