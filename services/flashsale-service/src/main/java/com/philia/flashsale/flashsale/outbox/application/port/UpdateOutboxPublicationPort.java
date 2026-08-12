package com.philia.flashsale.flashsale.outbox.application.port;

import java.time.Instant;
import java.util.UUID;

/** Records the outcome of an outbox publication attempt. */
public interface UpdateOutboxPublicationPort {
    void markPublished(UUID eventId, Instant publishedAt);

    void markFailed(UUID eventId, Instant failedAt, Instant nextAttemptAt, String sanitizedError);
}
