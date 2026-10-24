package com.housekeeping.platform.aftersales.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 售后工单状态机测试（docs/modules/11 第 6.3 节转换示例）。
 */
class TicketStatusTest {

    /** 全部状态，穷举用。 */
    private static final Set<TicketStatus> ALL = Set.of(TicketStatus.values());

    @Test
    @DisplayName("SUBMITTED 只能受理或驳回，不能直接形成结论或关闭")
    void submittedTransitions() {
        assertThat(TicketStatus.SUBMITTED.canTransitTo(TicketStatus.PROCESSING)).isTrue();
        assertThat(TicketStatus.SUBMITTED.canTransitTo(TicketStatus.REJECTED)).isTrue();
        for (TicketStatus target : ALL) {
            if (target != TicketStatus.PROCESSING && target != TicketStatus.REJECTED) {
                assertThat(TicketStatus.SUBMITTED.canTransitTo(target)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("PROCESSING 可以转挂起或形成结论，不能直接关闭")
    void processingTransitions() {
        assertThat(TicketStatus.PROCESSING.canTransitTo(TicketStatus.PENDING_CUSTOMER)).isTrue();
        assertThat(TicketStatus.PROCESSING.canTransitTo(TicketStatus.PENDING_WORKER)).isTrue();
        assertThat(TicketStatus.PROCESSING.canTransitTo(TicketStatus.PENDING_REVIEW)).isTrue();
        assertThat(TicketStatus.PROCESSING.canTransitTo(TicketStatus.RESOLVED)).isTrue();
        assertThat(TicketStatus.PROCESSING.canTransitTo(TicketStatus.REJECTED)).isTrue();
        assertThat(TicketStatus.PROCESSING.canTransitTo(TicketStatus.CLOSED)).isFalse();
        assertThat(TicketStatus.PROCESSING.canTransitTo(TicketStatus.SUBMITTED)).isFalse();
    }

    @Test
    @DisplayName("PENDING_* 可以回处理中或形成结论")
    void pendingTransitions() {
        for (TicketStatus pending : Set.of(TicketStatus.PENDING_CUSTOMER,
                TicketStatus.PENDING_WORKER, TicketStatus.PENDING_REVIEW)) {
            assertThat(pending.canTransitTo(TicketStatus.PROCESSING)).isTrue();
            assertThat(pending.canTransitTo(TicketStatus.RESOLVED)).isTrue();
            assertThat(pending.canTransitTo(TicketStatus.REJECTED)).isTrue();
            assertThat(pending.canTransitTo(TicketStatus.CLOSED)).isFalse();
            assertThat(pending.canTransitTo(TicketStatus.SUBMITTED)).isFalse();
        }
    }

    @Test
    @DisplayName("RESOLVED / REJECTED 只能关闭，CLOSED 是终态")
    void settledTransitions() {
        assertThat(TicketStatus.RESOLVED.canTransitTo(TicketStatus.CLOSED)).isTrue();
        assertThat(TicketStatus.RESOLVED.canTransitTo(TicketStatus.PROCESSING)).isFalse();
        assertThat(TicketStatus.REJECTED.canTransitTo(TicketStatus.CLOSED)).isTrue();
        assertThat(TicketStatus.REJECTED.canTransitTo(TicketStatus.PROCESSING)).isFalse();
        for (TicketStatus target : ALL) {
            assertThat(TicketStatus.CLOSED.canTransitTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("isPendingFeedback 只对 PENDING_* 为真")
    void pendingFeedbackFlag() {
        assertThat(TicketStatus.PENDING_CUSTOMER.isPendingFeedback()).isTrue();
        assertThat(TicketStatus.PENDING_WORKER.isPendingFeedback()).isTrue();
        assertThat(TicketStatus.PENDING_REVIEW.isPendingFeedback()).isTrue();
        assertThat(TicketStatus.PROCESSING.isPendingFeedback()).isFalse();
        assertThat(TicketStatus.RESOLVED.isPendingFeedback()).isFalse();
    }
}
