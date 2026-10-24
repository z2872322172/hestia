package com.housekeeping.platform.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 重试退避纯函数测试（docs/modules/12 第 14 节：1 分钟 / 5 分钟 / 30 分钟 / 2 小时）。
 */
class NotificationRetryBackoffTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 10, 23, 12, 0);

    @Test
    void firstFailureWaitsOneMinute() {
        assertThat(NotificationRetryBackoff.nextRetryAt(1, BASE))
                .isEqualTo(BASE.plusMinutes(1));
    }

    @Test
    void secondFailureWaitsFiveMinutes() {
        assertThat(NotificationRetryBackoff.nextRetryAt(2, BASE))
                .isEqualTo(BASE.plusMinutes(5));
    }

    @Test
    void thirdFailureWaitsThirtyMinutes() {
        assertThat(NotificationRetryBackoff.nextRetryAt(3, BASE))
                .isEqualTo(BASE.plusMinutes(30));
    }

    @Test
    void fourthFailureWaitsTwoHours() {
        assertThat(NotificationRetryBackoff.nextRetryAt(4, BASE))
                .isEqualTo(BASE.plusHours(2));
    }

    @Test
    void beyondSequenceCapsAtTwoHours() {
        assertThat(NotificationRetryBackoff.nextRetryAt(99, BASE))
                .isEqualTo(BASE.plusHours(2));
    }

    @Test
    void nonPositiveFailureCountFallsBackToFirstInterval() {
        assertThat(NotificationRetryBackoff.nextRetryAt(0, BASE))
                .isEqualTo(BASE.plusMinutes(1));
    }

    @Test
    void retryExhaustedAtMaxRetryCount() {
        assertThat(NotificationRetryBackoff.exhausted(5, 5)).isTrue();
        assertThat(NotificationRetryBackoff.exhausted(6, 5)).isTrue();
    }

    @Test
    void retryNotExhaustedBelowMax() {
        assertThat(NotificationRetryBackoff.exhausted(4, 5)).isFalse();
        assertThat(NotificationRetryBackoff.exhausted(0, 5)).isFalse();
    }
}
