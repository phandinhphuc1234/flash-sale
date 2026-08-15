package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCreatedAvroMapperTests {
    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsExactEnvelopeDataMoneyAndSingleItemWithoutInfrastructureFields() {
        UUID orderId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        var event = event(orderId, payload(orderId, purchaseRequestId, reservationId, campaignId, userId, variantId));

        var mapped = new OrderCreatedAvroMapper(new ObjectMapper()).map(event);

        assertThat(mapped.getEventId()).isEqualTo(event.eventId());
        assertThat(mapped.getEventType()).isEqualTo("OrderCreated");
        assertThat(mapped.getEventVersion()).isEqualTo(1);
        assertThat(mapped.getProducer()).isEqualTo("order-service");
        assertThat(mapped.getAggregateType()).isEqualTo("ORDER");
        assertThat(mapped.getAggregateId()).isEqualTo(orderId);
        assertThat(mapped.getCausationId()).isEqualTo(event.causationId());
        assertThat(mapped.getData().getOrderId()).isEqualTo(orderId);
        assertThat(mapped.getData().getSubtotalAmount()).isEqualByComparingTo("20.0000");
        assertThat(mapped.getData().getItems()).hasSize(1);
        assertThat(mapped.getData().getItems().get(0).getLineAmount()).isEqualByComparingTo("20.0000");
    }

    @Test
    void rejectsMismatchedAggregateAndPayloadIdentity() {
        UUID orderId = UUID.randomUUID();
        UUID payloadOrderId = UUID.randomUUID();
        var event = event(orderId, payload(payloadOrderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID()));

        assertThatThrownBy(() -> new OrderCreatedAvroMapper(new ObjectMapper()).map(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregate");
    }

    @Test
    void rejectsNonExactMoneyOrMultipleItems() {
        UUID orderId = UUID.randomUUID();
        String invalid = payload(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID()).replace("20.0000", "20.00001").replace("]}", "]},{\"variantId\":\""
                        + UUID.randomUUID() + "\",\"quantity\":1,\"unitPrice\":\"1.0000\",\"lineAmount\":\"1.0000\"}]} }");
        var event = event(orderId, invalid);

        assertThatThrownBy(() -> new OrderCreatedAvroMapper(new ObjectMapper()).map(event))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsForbiddenInternalFieldsAndInvalidContractSemantics() {
        UUID orderId = UUID.randomUUID();
        String forbidden = payload(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID()).replace("]}", "],\"outboxStatus\":\"PUBLISHED\"}");

        assertThatThrownBy(() -> new OrderCreatedAvroMapper(new ObjectMapper()).map(event(orderId, forbidden)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden");

        String invalidStatus = payload(orderId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID()).replace("PENDING_PAYMENT", "CONFIRMED");
        assertThatThrownBy(() -> new OrderCreatedAvroMapper(new ObjectMapper()).map(event(orderId, invalidStatus)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
    }

    private static OrderOutboxEvent event(UUID orderId, String payload) {
        return new OrderOutboxEvent(UUID.randomUUID(), "ORDER", orderId, 1, "OrderCreated", 1,
                orderId.toString(), UUID.randomUUID(), UUID.randomUUID(), payload,
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", "vendor=value", "IN_PROGRESS", 1,
                ACCEPTED, "worker", ACCEPTED.plusSeconds(30), null, null, ACCEPTED, ACCEPTED, ACCEPTED);
    }

    private static String payload(UUID orderId, UUID purchaseRequestId, UUID reservationId, UUID campaignId,
            UUID userId, UUID variantId) {
        return "{"
                + "\"orderId\":\"" + orderId + "\",\"orderNumber\":\"ORD-1\","
                + "\"purchaseRequestId\":\"" + purchaseRequestId + "\",\"reservationId\":\"" + reservationId + "\","
                + "\"campaignId\":\"" + campaignId + "\",\"userId\":\"" + userId + "\","
                + "\"status\":\"PENDING_PAYMENT\",\"currency\":\"VND\","
                + "\"subtotalAmount\":\"20.0000\",\"totalAmount\":\"20.0000\","
                + "\"acceptedAt\":\"" + ACCEPTED + "\",\"reservationExpiresAt\":\"" + ACCEPTED.plusSeconds(300) + "\","
                + "\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":2,"
                + "\"unitPrice\":\"10.0000\",\"lineAmount\":\"20.0000\"}]}";
    }
}
