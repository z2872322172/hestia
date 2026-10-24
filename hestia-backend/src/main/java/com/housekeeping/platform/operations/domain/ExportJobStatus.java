package com.housekeeping.platform.operations.domain;

/**
 * 导出任务状态机（docs/modules/13 第 14.4 节）。
 *
 * <pre>
 * PENDING → PROCESSING → SUCCESS / FAILED
 * PENDING → CANCELED
 * SUCCESS → EXPIRED（到期清理）
 * </pre>
 */
public enum ExportJobStatus {

    /** 待处理。 */
    PENDING,

    /** 生成中。 */
    PROCESSING,

    /** 生成成功，文件可下载。 */
    SUCCESS,

    /** 生成失败。 */
    FAILED,

    /** 文件已过期。 */
    EXPIRED,

    /** 发起人已取消。 */
    CANCELED;

    /**
     * 是否允许取消（只有还没被认领的任务可以取消）。
     *
     * @return 允许返回 {@code true}
     */
    public boolean canCancel() {
        return this == PENDING;
    }

    /**
     * 是否终态。
     *
     * @return 终态返回 {@code true}
     */
    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == EXPIRED || this == CANCELED;
    }
}
