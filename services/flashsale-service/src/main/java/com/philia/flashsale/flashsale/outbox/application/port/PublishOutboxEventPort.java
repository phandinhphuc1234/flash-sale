package com.philia.flashsale.flashsale.outbox.application.port;

import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;

/** Publishes one approved Flash Sale outbox event without exposing Kafka to the use case. */
public interface PublishOutboxEventPort {
    void publish(OutboxEvent event);
}
