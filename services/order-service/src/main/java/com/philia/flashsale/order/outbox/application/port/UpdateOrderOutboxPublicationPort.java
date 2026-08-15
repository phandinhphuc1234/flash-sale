package com.philia.flashsale.order.outbox.application.port;

import java.time.Instant;
import java.util.UUID;

/** Records publication outcomes while guarding updates by the active lease owner. */
public interface UpdateOrderOutboxPublicationPort {
    void markPublished(UUID eventId, String workerId, Instant publishedAt);

    void markFailed(UUID eventId, String workerId, Instant failedAt, Instant nextAttemptAt,
            String sanitizedError);
}
