package com.housekeeping.platform.workforce.application.view;

/**
 * 人员服务区域视图。
 *
 * @param serviceAreaId 服务区域 ID
 * @param areaNo        片区编号
 * @param areaName      片区名称
 * @param status        ACTIVE / DISABLED
 */
public record WorkerServiceAreaView(long serviceAreaId,
                                    String areaNo,
                                    String areaName,
                                    String status) {
}
