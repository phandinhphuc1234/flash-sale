package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderConfirmedAvroMapperTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsTheTerminalOrderFact() {
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        var event = event(orderId, payload(orderId, purchaseRequestId, reservationId, paymentId));

        var mapped = new OrderConfirmedAvroMapper(new ObjectMapper()).map(event);

        assertThat(mapped.getEventType()).isEqualTo("OrderConfirmed");
        assertThat(mapped.getProducer()).isEqualTo("order-service");
        assertThat(mapped.getAggregateId()).isEqualTo(orderId);
        assertThat(mapped.getData().getPaymentId()).isEqualTo(paymentId);
        assertThat(mapped.getData().getConfirmedAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsAConfirmedFactWhosePayloadOrderDiffersFromTheAggregate() {
        UUID orderId = UUID.randomUUID();
        assertThatThrownBy(() -> new OrderConfirmedAvroMapper(new ObjectMapper()).map(
                event(orderId, payload(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity");
    }

    private static OrderOutboxEvent event(UUID orderId, String payload) {
        return new OrderOutboxEvent(UUID.randomUUID(), "ORDER", orderId, 2, "OrderConfirmed", 1,
                orderId.toString(), UUID.randomUUID(), UUID.randomUUID(), payload,
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", null, "IN_PROGRESS", 1,
                NOW, "worker", NOW.plusSeconds(30), null, null, NOW, NOW, NOW);
    }

    private static String payload(UUID orderId, UUID purchaseRequestId, UUID reservationId, UUID paymentId) {
        return "{\"orderId\":\"" + orderId + "\",\"orderNumber\":\"ORD-1\","
                + "\"purchaseRequestId\":\"" + purchaseRequestId + "\","
                + "\"reservationId\":\"" + reservationId + "\",\"paymentId\":\"" + paymentId + "\","
                + "\"confirmedAt\":\"" + NOW + "\"}";
    }
}
