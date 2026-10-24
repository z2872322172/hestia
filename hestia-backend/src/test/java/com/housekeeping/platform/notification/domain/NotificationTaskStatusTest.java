package com.housekeeping.platform.notification.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知任务状态机测试（docs/modules/12 第 6 节流转表）。
 */
class NotificationTaskStatusTest {

    @Test
    void pendingCanOnlyGoProcessingOrCanceled() {
        assertThat(NotificationTaskStatus.PENDING.canTransitionTo(NotificationTaskStatus.PROCESSING)).isTrue();
        assertThat(NotificationTaskStatus.PENDING.canTransitionTo(NotificationTaskStatus.CANCELED)).isTrue();
        assertThat(NotificationTaskStatus.PENDING.canTransitionTo(NotificationTaskStatus.SENT)).isFalse();
        assertThat(NotificationTaskStatus.PENDING.canTransitionTo(NotificationTaskStatus.FAILED)).isFalse();
    }

    @Test
    void processingCanOnlyGoSentOrFailed() {
        assertThat(NotificationTaskStatus.PROCESSING.canTransitionTo(NotificationTaskStatus.SENT)).isTrue();
        assertThat(NotificationTaskStatus.PROCESSING.canTransitionTo(NotificationTaskStatus.FAILED)).isTrue();
        assertThat(NotificationTaskStatus.PROCESSING.canTransitionTo(NotificationTaskStatus.PENDING)).isFalse();
    }

    @Test
    void failedCanRequeueToPendingOnly() {
        assertThat(NotificationTaskStatus.FAILED.canTransitionTo(NotificationTaskStatus.PENDING)).isTrue();
        assertThat(NotificationTaskStatus.FAILED.canTransitionTo(NotificationTaskStatus.SENT)).isFalse();
        assertThat(NotificationTaskStatus.FAILED.canTransitionTo(NotificationTaskStatus.CANCELED)).isFalse();
    }

    @Test
    void sentAndCanceledAreTerminal() {
        for (NotificationTaskStatus target : NotificationTaskStatus.values()) {
            assertThat(NotificationTaskStatus.SENT.canTransitionTo(target)).isFalse();
            assertThat(NotificationTaskStatus.CANCELED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    void eventTypesSupportBookingFamilyOnly() {
        assertThat(NotificationEventTypes.supports("BOOKING_PAID")).isTrue();
        assertThat(NotificationEventTypes.supports("BOOKING_CREATED")).isTrue();
        assertThat(NotificationEventTypes.supports("BOOKING_RESCHEDULED")).isTrue();
        assertThat(NotificationEventTypes.supports("BOOKING_CANCELED")).isTrue();
        assertThat(NotificationEventTypes.supports("BOOKING_COMPLETED")).isTrue();
        assertThat(NotificationEventTypes.supports("AFTER_SALE_UPDATED")).isFalse();
        assertThat(NotificationEventTypes.supports(null)).isFalse();
    }
}
