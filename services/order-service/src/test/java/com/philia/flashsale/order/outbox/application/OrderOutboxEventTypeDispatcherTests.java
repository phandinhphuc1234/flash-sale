package com.philia.flashsale.order.outbox.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import com.philia.flashsale.order.outbox.application.usecase.OrderOutboxEventTypeDispatcher;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderOutboxEventTypeDispatcherTests {
    @Test
    void routesByApprovedEventTypeWithoutKnowingKafka() {
        var received = new java.util.ArrayList<UUID>();
        PublishOrderEventPort publisher = event -> received.add(event.eventId());
        var dispatcher = new OrderOutboxEventTypeDispatcher(Map.of("OrderCreated", publisher));
        var event = event("OrderCreated");

        dispatcher.publish(event);

        assertThat(received).containsExactly(event.eventId());
    }

    @Test
    void refusesAnEventUntilItsStoryRegistersAPublisher() {
        var dispatcher = new OrderOutboxEventTypeDispatcher(Map.of());

        assertThatThrownBy(() -> dispatcher.publish(event("PaymentRequested")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PaymentRequested");
    }

    private static OrderOutboxEvent event(String type) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        return new OrderOutboxEvent(id, "ORDER", id, 1, type, 1, id.toString(),
                UUID.randomUUID(), UUID.randomUUID(), "{}", "00-trace", null,
                "PENDING", 0, now, null, null, null, null, now, now, now);
    }
}
