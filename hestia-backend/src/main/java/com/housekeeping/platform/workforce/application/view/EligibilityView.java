package com.housekeeping.platform.workforce.application.view;

import java.util.List;

/**
 * 可派单资格视图（docs/modules/05 第 9、17.4 节）。
 *
 * <p>返回 {@code reasons} 的完整列表而不是只返回第一个原因：运营改完一轮就能过，
 * 否则会陷入"改一个、试一次"的循环。</p>
 *
 * @param workerNo       人员编号
 * @param serviceItemNo  服务项目编号
 * @param serviceAreaId  订单所在服务区域，{@code null} 表示未校验区域
 * @param eligible       是否可派单
 * @param reasons        不可派单原因（含编码与中文说明）
 */
public record EligibilityView(String workerNo,
                              String serviceItemNo,
                              Long serviceAreaId,
                              boolean eligible,
                              List<Reason> reasons) {

    /**
     * 单条原因。
     *
     * @param code        原因编码（如 {@code CERTIFICATE_EXPIRED}）
     * @param description 中文说明
     */
    public record Reason(String code, String description) {
    }
}
