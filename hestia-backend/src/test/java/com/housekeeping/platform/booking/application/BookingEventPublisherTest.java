package com.housekeeping.platform.booking.application;

import com.housekeeping.platform.booking.domain.BookingEventTypes;
import com.housekeeping.platform.booking.persistence.entity.BookingOrderEntity;
import com.housekeeping.platform.infrastructure.messaging.OutboxEventWriter;
import com.housekeeping.platform.infrastructure.messaging.rocketmq.RocketMqProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单事件发布器测试：验证五类事件的 Outbox 参数与载荷最小快照。
 *
 * <p>载荷是消费契约，字段名或口径漂移会静默破坏下游消费者，
 * 所以在这里钉死；信封（eventId/traceId/version）由 Relay 统一装配，不在本测试范围。</p>
 */
@ExtendWith(MockitoExtension.class)
class BookingEventPublisherTest {

    private static final String ORDER_NO = "BKG20261023000001";

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private BookingEventPublisher publisher;

    @BeforeEach
    void setUp() {
        RocketMqProperties properties = new RocketMqProperties();
        publisher = new BookingEventPublisher(outboxEventWriter, properties);
    }

    /** append 是 void 语义调用（返回值发布器不使用），stub 一下避免 strict stubs 报未使用。 */
    private void stubAppend() throws Exception {
        when(outboxEventWriter.append(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), any())).thenReturn(null);
    }

    private BookingOrderEntity order() {
        BookingOrderEntity order = new BookingOrderEntity();
        order.setOrderNo(ORDER_NO);
        order.setOrderStatus("PENDING_PAYMENT");
        order.setAppointmentDate(LocalDate.of(2026, 11, 17));
        order.setAppointmentStart(LocalDateTime.of(2026, 11, 17, 8, 0));
        order.setPayableAmount(new BigDecimal("199.00"));
        return order;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturedPayload() throws Exception {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).append(anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString(), captor.capture());
        return (Map<String, Object>) captor.getValue();
    }

    private String[] capturedHeader() throws Exception {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(outboxEventWriter).append(captor.capture(), captor.capture(), captor.capture(),
                captor.capture(), captor.capture(), captor.capture(), any());
        return captor.getAllValues().toArray(new String[0]);
    }

    @Test
    @DisplayName("下单事件：eventType 即 Tag，messageKey 取订单号，载荷为最小快照")
    void publishCreated() throws Exception {
        stubAppend();
        publisher.publishCreated(order());

        String[] header = capturedHeader();
        assertThat(header).containsExactly(BookingEventTypes.BOOKING_CREATED,
                BookingEventTypes.AGGREGATE_BOOKING_ORDER, ORDER_NO,
                "HK_BOOKING_EVENT", BookingEventTypes.BOOKING_CREATED, ORDER_NO);

        Map<String, Object> payload = capturedPayload();
        assertThat(payload)
                .containsEntry("orderNo", ORDER_NO)
                .containsEntry("status", "PENDING_PAYMENT")
                .containsEntry("payableAmount", new BigDecimal("199.00"));
        assertThat(payload).containsKeys("appointmentDate", "appointmentStart");
    }

    @Test
    @DisplayName("支付事件：状态置 CONFIRMED，未激活派单时 currentWorkerId 为 null")
    void publishPaid() throws Exception {
        stubAppend();
        publisher.publishPaid(order(), new BigDecimal("199.00"),
                LocalDateTime.of(2026, 10, 23, 12, 0), 0L);

        Map<String, Object> payload = capturedPayload();
        assertThat(payload)
                .containsEntry("status", "CONFIRMED")
                .containsEntry("paidAmount", new BigDecimal("199.00"))
                .containsEntry("currentWorkerId", null);
        assertThat(payload).containsKey("paidAt");
    }

    @Test
    @DisplayName("支付事件：派单已激活时载荷携带人员 ID")
    void publishPaidWithWorker() throws Exception {
        stubAppend();
        publisher.publishPaid(order(), new BigDecimal("199.00"),
                LocalDateTime.of(2026, 10, 23, 12, 0), 9527L);

        assertThat(capturedPayload()).containsEntry("currentWorkerId", 9527L);
    }

    @Test
    @DisplayName("改期事件：载荷同时记录原日期与新日期/新窗口/新人员")
    void publishRescheduled() throws Exception {
        stubAppend();
        publisher.publishRescheduled(order(), LocalDate.of(2026, 11, 18), "MORNING", 42L);

        Map<String, Object> payload = capturedPayload();
        assertThat(payload)
                .containsEntry("previousDate", LocalDate.of(2026, 11, 17))
                .containsEntry("newDate", LocalDate.of(2026, 11, 18))
                .containsEntry("newWindowCode", "MORNING")
                .containsEntry("newWorkerId", 42L);
    }

    @Test
    @DisplayName("取消事件：source 区分触发方，空原因不进载荷")
    void publishCanceled() throws Exception {
        stubAppend();
        publisher.publishCanceled(order(), "CUSTOMER_CANCEL", "客户改期", "CUSTOMER");

        Map<String, Object> payload = capturedPayload();
        assertThat(payload)
                .containsEntry("status", "CANCELED")
                .containsEntry("reasonCode", "CUSTOMER_CANCEL")
                .containsEntry("source", "CUSTOMER");
    }

    @Test
    @DisplayName("取消事件：reasonCode 为空时载荷不含该键")
    void publishCanceledWithoutReason() throws Exception {
        stubAppend();
        publisher.publishCanceled(order(), null, null, "SYSTEM");

        Map<String, Object> payload = capturedPayload();
        assertThat(payload)
                .containsEntry("source", "SYSTEM")
                .doesNotContainKeys("reasonCode", "reasonText");
    }

    @Test
    @DisplayName("完成事件：forced 标记区分正常确认与运营强制完成")
    void publishCompleted() throws Exception {
        stubAppend();
        BookingOrderEntity completed = order();
        completed.setOrderStatus("COMPLETED");
        publisher.publishCompleted(completed, true);

        Map<String, Object> payload = capturedPayload();
        assertThat(payload)
                .containsEntry("status", "COMPLETED")
                .containsEntry("forced", true);
    }
}
