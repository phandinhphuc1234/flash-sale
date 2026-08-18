package com.philia.flashsale.payment.payment.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable reconciliation work and reclaimable lease capability. */
public interface PaymentRecoveryWorkPort {

    Work schedule(Work work);

    List<Work> claimWorkBatch(Instant now, int batchSize, String leaseOwner, Instant leaseUntil);

    void complete(UUID workId, Instant completedAt);

    /**
     * Returns a claimed item to the durable queue after a bounded provider/storage retry.
     *
     * <p>The default keeps existing in-memory adapters source-compatible; the PostgreSQL adapter
     * overrides it to persist the lease release, error class, and next eligible time.</p>
     */
    default void reschedule(UUID workId, String errorCode, Instant nextAttemptAt,
            Instant observedAt) {
        // Optional for lightweight test adapters; durable adapters must override this method.
    }

    void markManualReview(UUID workId, String errorCode, Instant observedAt);

    record Work(UUID id, UUID paymentId, UUID attemptId, String workType, String status,
            String providerIdempotencyKey, Instant safeReplayUntil, int attemptCount,
            Instant nextAttemptAt, String leaseOwner, Instant leaseUntil, String lastErrorCode,
            Instant createdAt, Instant updatedAt) {
    }
}
