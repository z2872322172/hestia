package com.housekeeping.platform.operations.domain;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;

/**
 * 导出数据类型（docs/modules/13 第 14.4 节 CHECK 约束）。
 *
 * <p>首版仅开放订单导出（REPORT_EXPORT_NOT_ALLOWED 口径）；
 * 人员、支付、售后、审计导出需要单独评审隐私影响后再放开。</p>
 */
public enum ExportType {

    /** 订单导出。 */
    ORDERS,

    /** 人员导出。 */
    WORKERS,

    /** 支付导出。 */
    PAYMENTS,

    /** 售后导出。 */
    AFTER_SALES,

    /** 审计日志导出。 */
    AUDIT;

    /**
     * 按编码解析；未知类型按"不允许导出"处理，避免暴露类型枚举细节。
     *
     * @param code 类型编码
     * @return 导出类型
     */
    public static ExportType fromCode(String code) {
        try {
            return valueOf(code);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(ErrorCode.REPORT_EXPORT_NOT_ALLOWED);
        }
    }

    /**
     * 当前版本是否允许导出。
     *
     * @return 允许返回 {@code true}
     */
    public boolean isExportable() {
        return this == ORDERS;
    }
}
