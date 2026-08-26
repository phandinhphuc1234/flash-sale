package com.philia.flashsale.order.purchasesaga.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.order.outbox.adapter.out.messaging.kafka.OrderPaymentReviewRequiredAvroMapper;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPaymentReviewRequiredMapperTests {
    @Test
    void mapsStableCorrectionEnvelopeAndApprovedReason() {
        UUID orderId = UUID.randomUUID();
        Instant now = Instant.parse("2030-01-01T10:00:00Z");
        var event = new OrderOutboxEvent(UUID.randomUUID(), "ORDER", orderId, 4,
                "OrderPaymentReviewRequired", 1, orderId.toString(), UUID.randomUUID(),
                UUID.randomUUID(), "{"
                + "\"orderId\":\"" + orderId + "\",\"orderNumber\":\"O-1\","
                + "\"purchaseRequestId\":\"" + UUID.randomUUID() + "\","
                + "\"reservationId\":\"" + UUID.randomUUID() + "\","
                + "\"paymentId\":\"" + UUID.randomUUID() + "\","
                + "\"previousStatus\":\"CANCELLED\","
                + "\"reviewReason\":\"LATE_PAYMENT_RESERVATION_UNAVAILABLE\","
                + "\"reviewRequiredAt\":\"" + now + "\"}", null, null, "PENDING", 0,
                now, null, null, null, null, now, now, now);

        var mapped = new OrderPaymentReviewRequiredAvroMapper(new ObjectMapper()).map(event);

        assertThat(mapped.getEventType().toString()).isEqualTo("OrderPaymentReviewRequired");
        assertThat(mapped.getAggregateVersion()).isEqualTo(4L);
        assertThat(mapped.getData().getReviewReason().toString())
                .isEqualTo("LATE_PAYMENT_RESERVATION_UNAVAILABLE");
    }
}
