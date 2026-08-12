package com.philia.flashsale.flashsale.outbox.application.usecase;

import com.philia.flashsale.flashsale.outbox.application.model.OutboxEvent;
import com.philia.flashsale.flashsale.outbox.application.port.ClaimOutboxEventsPort;
import com.philia.flashsale.flashsale.outbox.application.port.PublishPurchaseAcceptedPort;
import com.philia.flashsale.flashsale.outbox.application.port.UpdateOutboxPublicationPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Claims, publishes, and retries durable events without holding a database transaction over Kafka. */
public final class OutboxPublicationService {
    private final ClaimOutboxEventsPort claims;
    private final PublishPurchaseAcceptedPort publisher;
    private final UpdateOutboxPublicationPort updates;
    private final OutboxRetryPolicy retryPolicy;
    private final int batchSize;
    private final Duration claimLease;

    public OutboxPublicationService(ClaimOutboxEventsPort claims, PublishPurchaseAcceptedPort publisher,
            UpdateOutboxPublicationPort updates, OutboxRetryPolicy retryPolicy, int batchSize,
            Duration claimLease) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.updates = Objects.requireNonNull(updates, "updates");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        this.claimLease = Objects.requireNonNull(claimLease, "claimLease");
    }

    public int publishDue(String workerId, Instant now) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(now, "now");
        List<OutboxEvent> events = claims.claim(workerId, now, batchSize, claimLease);
        for (OutboxEvent event : events) {
            publishOne(event, now);
        }
        return events.size();
    }

    private void publishOne(OutboxEvent event, Instant now) {
        try {
            publisher.publish(event);
            updates.markPublished(event.eventId(), now);
        } catch (RuntimeException failure) {
            Duration delay = retryPolicy.delayForAttempt(event.attemptCount());
            updates.markFailed(event.eventId(), now, now.plus(delay), sanitize(failure));
        }
    }

    private String sanitize(RuntimeException failure) {
        return "outbox publication failed: " + failure.getClass().getSimpleName();
    }
}
