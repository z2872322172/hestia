package com.housekeeping.platform.customer.application.view;

/**
 * 地址视图（客户本人查看，返回完整信息）。
 *
 * @param addressNo       地址编号
 * @param label           标签
 * @param contactName     联系人
 * @param contactPhone    联系电话（完整，仅本人可见）
 * @param region          城市与区县
 * @param communityName   小区
 * @param buildingNo      楼栋
 * @param unitNo          单元
 * @param floorNo         楼层
 * @param roomNo          门牌
 * @param detailAddress   详细地址
 * @param accessNote      履约说明
 * @param isDefault       是否默认
 * @param areaMatchStatus 服务区域匹配状态
 * @param serviceAreaId   命中的服务区域 ID
 * @param version         版本号，修改时回传
 */
public record AddressView(String addressNo, String label, String contactName, String contactPhone,
                          AddressRegionView region, String communityName, String buildingNo,
                          String unitNo, String floorNo, String roomNo, String detailAddress,
                          String accessNote, Boolean isDefault, String areaMatchStatus,
                          Long serviceAreaId, Long version) {
}
