package com.housekeeping.platform.booking.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单主状态机测试（docs/modules/07 第 6.3 节转换矩阵）。
 */
class BookingStatusTest {

    /** 全部状态，穷举用。 */
    private static final Set<BookingStatus> ALL = Set.of(BookingStatus.values());

    @Test
    @DisplayName("PENDING_PAYMENT 只能到 CONFIRMED / CANCELED")
    void pendingPaymentTransitions() {
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitTo(BookingStatus.CONFIRMED)).isTrue();
        assertThat(BookingStatus.PENDING_PAYMENT.canTransitTo(BookingStatus.CANCELED)).isTrue();
        for (BookingStatus target : ALL) {
            if (target != BookingStatus.CONFIRMED && target != BookingStatus.CANCELED) {
                assertThat(BookingStatus.PENDING_PAYMENT.canTransitTo(target)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("CONFIRMED 可以出发或取消，不能直接进入服务")
    void confirmedTransitions() {
        assertThat(BookingStatus.CONFIRMED.canTransitTo(BookingStatus.EN_ROUTE)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canTransitTo(BookingStatus.CANCELED)).isTrue();
        assertThat(BookingStatus.CONFIRMED.canTransitTo(BookingStatus.IN_SERVICE)).isFalse();
        assertThat(BookingStatus.CONFIRMED.canTransitTo(BookingStatus.COMPLETED)).isFalse();
    }

    @Test
    @DisplayName("EN_ROUTE 只能进入服务或取消")
    void enRouteTransitions() {
        assertThat(BookingStatus.EN_ROUTE.canTransitTo(BookingStatus.IN_SERVICE)).isTrue();
        assertThat(BookingStatus.EN_ROUTE.canTransitTo(BookingStatus.CANCELED)).isTrue();
        assertThat(BookingStatus.EN_ROUTE.canTransitTo(BookingStatus.PENDING_CONFIRM)).isFalse();
    }

    @Test
    @DisplayName("IN_SERVICE 只能申请完成，不能取消（走售后）")
    void inServiceTransitions() {
        assertThat(BookingStatus.IN_SERVICE.canTransitTo(BookingStatus.PENDING_CONFIRM)).isTrue();
        for (BookingStatus target : ALL) {
            if (target != BookingStatus.PENDING_CONFIRM) {
                assertThat(BookingStatus.IN_SERVICE.canTransitTo(target)).isFalse();
            }
        }
    }

    @Test
    @DisplayName("PENDING_CONFIRM 只能完成")
    void pendingConfirmTransitions() {
        assertThat(BookingStatus.PENDING_CONFIRM.canTransitTo(BookingStatus.COMPLETED)).isTrue();
        assertThat(BookingStatus.PENDING_CONFIRM.canTransitTo(BookingStatus.CANCELED)).isFalse();
    }

    @Test
    @DisplayName("COMPLETED 与 CANCELED 是终态")
    void terminalStates() {
        for (BookingStatus target : ALL) {
            assertThat(BookingStatus.COMPLETED.canTransitTo(target)).isFalse();
            assertThat(BookingStatus.CANCELED.canTransitTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("可取消：待支付与已支付（未出发），服务开始后不可")
    void cancellableStates() {
        assertThat(BookingStatus.PENDING_PAYMENT.cancellable()).isTrue();
        assertThat(BookingStatus.CONFIRMED.cancellable()).isTrue();
        assertThat(BookingStatus.EN_ROUTE.cancellable()).isFalse();
        assertThat(BookingStatus.IN_SERVICE.cancellable()).isFalse();
        assertThat(BookingStatus.PENDING_CONFIRM.cancellable()).isFalse();
        assertThat(BookingStatus.COMPLETED.cancellable()).isFalse();
        assertThat(BookingStatus.CANCELED.cancellable()).isFalse();
    }

    @Test
    @DisplayName("可改期：仅已支付且未出发")
    void reschedulableStates() {
        assertThat(BookingStatus.CONFIRMED.reschedulable()).isTrue();
        assertThat(BookingStatus.PENDING_PAYMENT.reschedulable()).isFalse();
        assertThat(BookingStatus.EN_ROUTE.reschedulable()).isFalse();
    }

    @Test
    @DisplayName("已支付态：CONFIRMED 及以后，PENDING_PAYMENT 除外")
    void paidStates() {
        assertThat(BookingStatus.PENDING_PAYMENT.paid()).isFalse();
        assertThat(BookingStatus.CONFIRMED.paid()).isTrue();
        assertThat(BookingStatus.COMPLETED.paid()).isTrue();
        assertThat(BookingStatus.CANCELED.paid()).isFalse();
    }
}
