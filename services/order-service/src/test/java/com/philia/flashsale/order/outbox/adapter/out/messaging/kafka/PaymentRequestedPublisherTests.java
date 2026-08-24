package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentRequestedPublisherTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final String TRACEPARENT = "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01";

    @Test
    void mapsSagaOutboxToTheExistingPaymentRequestedContractWithOrderKeyIdentity() {
        UUID orderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        OrderOutboxEvent event = event(orderId, userId);

        PaymentRequestedV1 mapped = new PaymentRequestedAvroMapper(new ObjectMapper()).map(event);

        assertThat(mapped.getEventId()).isEqualTo(event.eventId());
        assertThat(mapped.getAggregateType()).isEqualTo("ORDER");
        assertThat(mapped.getAggregateId()).isEqualTo(orderId);
        assertThat(mapped.getCorrelationId()).isEqualTo(event.correlationId());
        assertThat(mapped.getCausationId()).isEqualTo(event.causationId());
        assertThat(mapped.getData().getOrderId()).isEqualTo(orderId);
        assertThat(mapped.getData().getUserId()).isEqualTo(userId);
        assertThat(mapped.getData().getAmount()).isEqualByComparingTo("20.0000");
        assertThat(mapped.getData().getPaymentDeadline()).isEqualTo(NOW.plusSeconds(270));
    }

    @Test
    void rejectsARequestWithAnOrderKeyDifferentFromPayloadOrder() {
        UUID orderId = UUID.randomUUID();
        OrderOutboxEvent event = event(orderId, UUID.randomUUID(), UUID.randomUUID().toString());

        assertThatThrownBy(() -> new PaymentRequestedAvroMapper(new ObjectMapper()).map(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("event key");
    }

    private OrderOutboxEvent event(UUID orderId, UUID userId) {
        return event(orderId, userId, orderId.toString());
    }

    private OrderOutboxEvent event(UUID orderId, UUID userId, String eventKey) {
        UUID eventId = UUID.randomUUID();
        return new OrderOutboxEvent(eventId, "PURCHASE_SAGA", orderId, 1,
                "PaymentRequested", 1, eventKey, UUID.randomUUID(), UUID.randomUUID(),
                "{\"orderId\":\"" + orderId + "\",\"userId\":\"" + userId
                        + "\",\"amount\":\"20.0000\",\"currency\":\"VND\","
                        + "\"paymentDeadline\":\"" + NOW.plusSeconds(270) + "\"}",
                TRACEPARENT, "vendor=value", "PENDING", 0, NOW, null, null, null, null,
                NOW, NOW, NOW);
    }
}
