package com.philia.flashsale.flashsale.outbox.application.usecase;

import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import com.philia.flashsale.flashsale.outbox.application.port.PublishOutboxEventPort;
import java.util.Map;
import java.util.Objects;

/** Selects a publisher by the versioned event type while keeping Kafka in adapters. */
public final class FlashSaleOutboxEventTypeDispatcher implements PublishOutboxEventPort {
    private final Map<String, PublishOutboxEventPort> publishers;

    public FlashSaleOutboxEventTypeDispatcher(Map<String, PublishOutboxEventPort> publishers) {
        this.publishers = Map.copyOf(Objects.requireNonNull(publishers, "publishers"));
    }

    @Override
    public void publish(OutboxEvent event) {
        Objects.requireNonNull(event, "event");
        PublishOutboxEventPort publisher = publishers.get(event.eventType());
        if (publisher == null) {
            throw new IllegalStateException("No Flash Sale outbox publisher registered for event type "
                    + event.eventType());
        }
        publisher.publish(event);
    }
}
