package com.philia.flashsale.payment.outbox.application.port;

import java.time.Instant;
import java.util.UUID;

/** Updates a claimed row only while the publishing worker still owns its lease. */
public interface UpdatePaymentOutboxPort {

    boolean markPublished(UUID eventId, String leaseOwner, Instant publishedAt);

    boolean recordFailure(UUID eventId, String leaseOwner, Instant failedAt,
            String sanitizedErrorCode, Instant nextAttemptAt);
}
