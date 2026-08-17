package com.philia.flashsale.payment.outbox.application.port;

import java.time.Instant;
import java.util.UUID;

/** Atomic persistence capability for stable Payment facts. */
public interface SavePaymentOutboxPort {

    OutboxRecord save(OutboxRecord record);

    record OutboxRecord(UUID eventId, UUID aggregateId, long aggregateVersion,
            String eventType, int eventVersion, String topicName, UUID messageKey,
            String payload, String traceparent, String tracestate, String status,
            int attemptCount, Instant nextAttemptAt, String leaseOwner, Instant leaseUntil,
            Instant publishedAt, Instant createdAt) {
    }
}
