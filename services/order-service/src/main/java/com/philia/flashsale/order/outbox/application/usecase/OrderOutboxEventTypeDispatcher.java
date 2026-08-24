package com.philia.flashsale.order.outbox.application.usecase;

import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import java.util.Map;
import java.util.Objects;

/** Selects a publisher by the versioned event type while keeping Kafka in adapters. */
public final class OrderOutboxEventTypeDispatcher implements PublishOrderEventPort {
    private final Map<String, PublishOrderEventPort> publishers;

    public OrderOutboxEventTypeDispatcher(Map<String, PublishOrderEventPort> publishers) {
        this.publishers = Map.copyOf(Objects.requireNonNull(publishers, "publishers"));
    }

    @Override
    public void publish(OrderOutboxEvent event) {
        Objects.requireNonNull(event, "event");
        PublishOrderEventPort publisher = publishers.get(event.eventType());
        if (publisher == null) {
            throw new IllegalStateException("No Order outbox publisher registered for event type "
                    + event.eventType());
        }
        publisher.publish(event);
    }
}
