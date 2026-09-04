package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.order.event.v2.OrderCreatedV2;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCreatedV2AvroMapperTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsRegularBuyNowWithoutInventingFlashSaleFields() {
        OrderOutboxEvent event = event();

        OrderCreatedV2 mapped = new OrderCreatedV2AvroMapper(new ObjectMapper()).map(event);

        assertThat(mapped.getEventType()).isEqualTo("OrderCreated");
        assertThat(mapped.getEventVersion()).isEqualTo(2);
        assertThat(mapped.getAggregateId()).isEqualTo(event.aggregateId());
        assertThat(mapped.getData().getPurchaseSource()).isEqualTo("BUY_NOW");
        assertThat(mapped.getData().getStockParticipantType()).isEqualTo("REGULAR_STOCK_HOLD");
        assertThat(mapped.getData().getCartId()).isNull();
        assertThat(mapped.getData().getItems()).hasSize(1);
        assertThat(mapped.getData().getPaymentDeadline()).isEqualTo(NOW.plusSeconds(270));
    }

    @Test
    void rejectsCartIdentityThatDoesNotMatchThePurchaseSource() {
        OrderOutboxEvent event = event("\"cartId\":\"" + UUID.randomUUID() + "\",\"cartVersion\":1,");

        assertThatThrownBy(() -> new OrderCreatedV2AvroMapper(new ObjectMapper()).map(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cart identity");
    }

    private static OrderOutboxEvent event() {
        return event("\"cartId\":null,\"cartVersion\":null,");
    }

    private static OrderOutboxEvent event(String cartFields) {
        UUID orderId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        String payload = "{"
                + "\"orderId\":\"" + orderId + "\",\"orderNumber\":\"ORD-001\","
                + "\"purchaseRequestId\":\"" + requestId + "\",\"userId\":\"" + userId + "\","
                + "\"purchaseSource\":\"BUY_NOW\",\"stockParticipantType\":\"REGULAR_STOCK_HOLD\","
                + "\"stockReferenceId\":\"" + holdId + "\"," + cartFields
                + "\"status\":\"PENDING_PAYMENT\",\"currency\":\"VND\","
                + "\"subtotalAmount\":\"20.0000\",\"totalAmount\":\"20.0000\","
                + "\"acceptedAt\":\"" + NOW + "\",\"stockHoldExpiresAt\":\"" + NOW.plusSeconds(300) + "\","
                + "\"paymentDeadline\":\"" + NOW.plusSeconds(270) + "\","
                + "\"items\":[{\"variantId\":\"" + variantId + "\",\"quantity\":2,"
                + "\"unitPrice\":\"10.0000\",\"lineAmount\":\"20.0000\"}]}";
        return new OrderOutboxEvent(UUID.randomUUID(), "ORDER", orderId, 1, "OrderCreatedV2", 2,
                orderId.toString(), requestId, UUID.randomUUID(), payload,
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", null,
                "PENDING", 0, NOW, null, null, null, null, NOW, NOW, NOW);
    }
}
