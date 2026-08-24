package com.philia.flashsale.order.outbox.application.port;

import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;

/** Publishes one approved Order-owned outbox event without exposing Kafka to the use case. */
public interface PublishOrderEventPort {
    void publish(OrderOutboxEvent event);
}
