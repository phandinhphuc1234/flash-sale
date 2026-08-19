package com.philia.flashsale.payment.payment.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable client-operation idempotency capability keyed by a digest, never by raw key material. */
public interface PaymentClientIdempotencyPort {

    Optional<Record> findLocked(String operation, String keyDigest);

    Optional<Record> findLockedById(UUID id);

    Record create(UUID id, String operation, String keyDigest, UUID userId, UUID paymentId,
            String requestFingerprint, Instant createdAt);

    Record attachAttempt(UUID id, UUID attemptId, String outcomeStatus, Instant updatedAt);

    record Record(UUID id, String operation, String keyDigest, UUID userId, UUID paymentId,
            String requestFingerprint, UUID attemptId, String outcomeStatus,
            Instant createdAt, Instant updatedAt) {
    }
}
