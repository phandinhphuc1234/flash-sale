package com.philia.flashsale.order.outbox.application.usecase;

import com.philia.flashsale.order.outbox.application.model.OrderOutboxEvent;
import com.philia.flashsale.order.outbox.application.port.ClaimOrderOutboxEventsPort;
import com.philia.flashsale.order.outbox.application.port.PublishOrderEventPort;
import com.philia.flashsale.order.outbox.application.port.UpdateOrderOutboxPublicationPort;
import com.philia.flashsale.order.observability.OrderObservability;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Claims, publishes, and retries committed facts without holding a database transaction over Kafka. */
public final class OrderOutboxPublicationService {
    private final ClaimOrderOutboxEventsPort claims;
    private final PublishOrderEventPort publisher;
    private final UpdateOrderOutboxPublicationPort updates;
    private final OrderOutboxRetryPolicy retryPolicy;
    private final int batchSize;
    private final Duration claimLease;
    private final OrderObservability observability;

    public OrderOutboxPublicationService(ClaimOrderOutboxEventsPort claims,
            PublishOrderEventPort publisher, UpdateOrderOutboxPublicationPort updates,
            OrderOutboxRetryPolicy retryPolicy, int batchSize, Duration claimLease) {
        this(claims, publisher, updates, retryPolicy, batchSize, claimLease, OrderObservability.noop());
    }

    public OrderOutboxPublicationService(ClaimOrderOutboxEventsPort claims,
            PublishOrderEventPort publisher, UpdateOrderOutboxPublicationPort updates,
            OrderOutboxRetryPolicy retryPolicy, int batchSize, Duration claimLease,
            OrderObservability observability) {
        this.claims = Objects.requireNonNull(claims, "claims");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.updates = Objects.requireNonNull(updates, "updates");
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        this.batchSize = batchSize;
        this.claimLease = Objects.requireNonNull(claimLease, "claimLease");
        if (claimLease.isZero() || claimLease.isNegative()) {
            throw new IllegalArgumentException("claim lease must be positive");
        }
        this.observability = Objects.requireNonNull(observability, "observability");
    }

    public int publishDue(String workerId, Instant now) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(now, "now");
        List<OrderOutboxEvent> events = claims.claim(workerId, now, batchSize, claimLease);
        for (OrderOutboxEvent event : events) {
            publishOne(event, workerId, now);
        }
        return events.size();
    }

    private void publishOne(OrderOutboxEvent event, String workerId, Instant now) {
        observability.observe(OrderObservability.Operation.OUTBOX_PUBLICATION, () -> {
            try {
                publisher.publish(event);
                updates.markPublished(event.eventId(), workerId, now);
            } catch (RuntimeException failure) {
                Duration delay = retryPolicy.delayForAttempt(event.attemptCount());
                updates.markFailed(event.eventId(), workerId, now, now.plus(delay), sanitize(failure));
            }
        });
    }

    private String sanitize(RuntimeException failure) {
        return "outbox publication failed: " + failure.getClass().getSimpleName();
    }
}
