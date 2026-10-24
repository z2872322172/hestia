package com.housekeeping.platform.operations.domain;

import com.housekeeping.platform.common.api.ErrorCode;
import com.housekeeping.platform.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 导出状态机与类型测试（docs/modules/13 第 20.1 节"导出状态转换"）。
 */
class ExportJobStatusTest {

    @Test
    @DisplayName("只有 PENDING 可以取消")
    void onlyPendingCanBeCanceled() {
        assertThat(ExportJobStatus.PENDING.canCancel()).isTrue();
        assertThat(ExportJobStatus.PROCESSING.canCancel()).isFalse();
        assertThat(ExportJobStatus.SUCCESS.canCancel()).isFalse();
        assertThat(ExportJobStatus.FAILED.canCancel()).isFalse();
        assertThat(ExportJobStatus.EXPIRED.canCancel()).isFalse();
        assertThat(ExportJobStatus.CANCELED.canCancel()).isFalse();
    }

    @Test
    @DisplayName("终态判定")
    void terminalStates() {
        assertThat(ExportJobStatus.SUCCESS.isTerminal()).isTrue();
        assertThat(ExportJobStatus.FAILED.isTerminal()).isTrue();
        assertThat(ExportJobStatus.EXPIRED.isTerminal()).isTrue();
        assertThat(ExportJobStatus.CANCELED.isTerminal()).isTrue();
        assertThat(ExportJobStatus.PENDING.isTerminal()).isFalse();
        assertThat(ExportJobStatus.PROCESSING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("首版仅 ORDERS 允许导出")
    void onlyOrdersExportable() {
        assertThat(ExportType.ORDERS.isExportable()).isTrue();
        assertThat(ExportType.WORKERS.isExportable()).isFalse();
        assertThat(ExportType.PAYMENTS.isExportable()).isFalse();
        assertThat(ExportType.AFTER_SALES.isExportable()).isFalse();
        assertThat(ExportType.AUDIT.isExportable()).isFalse();
    }

    @Test
    @DisplayName("未知导出类型按不允许导出处理")
    void unknownTypeRejected() {
        assertThatThrownBy(() -> ExportType.fromCode("NOT_EXIST"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(ErrorCode.REPORT_EXPORT_NOT_ALLOWED));
    }
}
