package com.housekeeping.platform.customer.application.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 下单地址快照（docs/modules/02 第 15.1 节）。
 *
 * <p>快照由服务端读取并生成，保存<b>名称</b>而不只是编码：
 * 行政区改名、服务区域调整后，历史订单上的地址描述必须保持下单时的样子。</p>
 *
 * @param addressId         地址 ID
 * @param addressNo         地址编号
 * @param addressVersion    地址版本号，用于证明快照取自哪一版
 * @param contactName       联系人
 * @param contactCountryCode 联系人区号
 * @param contactPhone      联系电话
 * @param provinceCode      省编码
 * @param provinceName      省名称
 * @param cityCode          市编码
 * @param cityName          市名称
 * @param districtCode      区县编码
 * @param districtName      区县名称
 * @param streetCode        街道编码
 * @param streetName        街道名称
 * @param serviceAreaId     服务区域 ID
 * @param communityName     小区
 * @param buildingNo        楼栋
 * @param unitNo            单元
 * @param floorNo           楼层
 * @param roomNo            门牌
 * @param detailAddress     详细地址
 * @param longitude         经度
 * @param latitude          纬度
 * @param accessNote        履约说明
 * @param snapshotAt        快照生成时间
 */
public record AddressSnapshot(long addressId, String addressNo, long addressVersion,
                              String contactName, String contactCountryCode, String contactPhone,
                              String provinceCode, String provinceName, String cityCode, String cityName,
                              String districtCode, String districtName, String streetCode, String streetName,
                              Long serviceAreaId, String communityName, String buildingNo, String unitNo,
                              String floorNo, String roomNo, String detailAddress,
                              BigDecimal longitude, BigDecimal latitude, String accessNote,
                              LocalDateTime snapshotAt) {
}
