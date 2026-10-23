package com.housekeeping.platform.catalog.application.view;

/**
 * 跨模块使用的服务项目摘要。
 *
 * <p>只带"身份 + 状态 + 时长"，不带价格。人员技能、资质要求、排班时长校验、下单校验都需要它，
 * 但它们没有算价的资格——价格只能由 {@code PriceQuoteService} 给出。
 * 时长供 scheduling 判断"服务能否放入时间窗"，属身份属性而非价格属性。</p>
 *
 * @param serviceItemId   服务项目 ID
 * @param serviceItemNo   服务项目编号
 * @param serviceItemName 服务项目名称
 * @param durationMinutes 预计基础时长（分钟），选项增量不计入——排班只对齐基础时长
 * @param status          状态（{@code DRAFT} / {@code PUBLISHED} / {@code OFFLINE}）
 */
public record ServiceItemRef(long serviceItemId, String serviceItemNo,
                             String serviceItemName, int durationMinutes, String status) {

    /**
     * 是否已上架。
     *
     * @return 状态为 {@code PUBLISHED} 返回 {@code true}
     */
    public boolean published() {
        return "PUBLISHED".equals(status);
    }
}
