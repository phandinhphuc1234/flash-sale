package com.philia.flashsale.order.outbox.adapter.out.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.contract.regularhold.command.v1.ConfirmRegularStockHoldV1;
import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConfirmRegularStockHoldAvroMapperTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Test
    void mapsPaidRegularHoldCommandWithExactSagaOrderAndPaymentIdentity() {
        OrderOutboxEvent event = event();

        ConfirmRegularStockHoldV1 mapped = new ConfirmRegularStockHoldAvroMapper(new ObjectMapper()).map(event);

        assertThat(mapped.getEventType()).isEqualTo("ConfirmRegularStockHold");
        assertThat(mapped.getAggregateId()).isEqualTo(event.aggregateId());
        assertThat(mapped.getCorrelationId()).isEqualTo(event.correlationId());
        assertThat(mapped.getCausationId()).isEqualTo(event.causationId());
        assertThat(mapped.getData().getOrderId().toString()).isEqualTo(event.eventKey());
    }

    @Test
    void rejectsACommandWhoseCausationIsNotThePaidPayment() {
        OrderOutboxEvent event = event(UUID.randomUUID());

        assertThatThrownBy(() -> new ConfirmRegularStockHoldAvroMapper(new ObjectMapper()).map(event))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity/key");
    }

    private static OrderOutboxEvent event() {
        UUID paymentId = UUID.randomUUID();
        return event(paymentId, paymentId);
    }

    private static OrderOutboxEvent event(UUID causationId) {
        return event(UUID.randomUUID(), causationId);
    }

    private static OrderOutboxEvent event(UUID paymentId, UUID causationId) {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        String payload = "{\"sagaId\":\"" + sagaId + "\",\"orderId\":\"" + orderId
                + "\",\"purchaseRequestId\":\"" + requestId + "\",\"holdId\":\"" + holdId
                + "\",\"paymentId\":\"" + paymentId + "\",\"paidAt\":\"" + NOW + "\"}";
        return new OrderOutboxEvent(UUID.randomUUID(), "PURCHASE_SAGA", sagaId, 2,
                "ConfirmRegularStockHold", 1, orderId.toString(), requestId, causationId, payload,
                "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", "vendor=value",
                "PENDING", 0, NOW, null, null, null, null, NOW, NOW, NOW);
    }
}
