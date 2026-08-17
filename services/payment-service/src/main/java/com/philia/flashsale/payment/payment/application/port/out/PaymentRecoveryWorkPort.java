package com.philia.flashsale.payment.payment.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable reconciliation work and reclaimable lease capability. */
public interface PaymentRecoveryWorkPort {

    Work schedule(Work work);

    List<Work> claimWorkBatch(Instant now, int batchSize, String leaseOwner, Instant leaseUntil);

    void complete(UUID workId, Instant completedAt);

    void markManualReview(UUID workId, String errorCode, Instant observedAt);

    record Work(UUID id, UUID paymentId, UUID attemptId, String workType, String status,
            String providerIdempotencyKey, Instant safeReplayUntil, int attemptCount,
            Instant nextAttemptAt, String leaseOwner, Instant leaseUntil, String lastErrorCode,
            Instant createdAt, Instant updatedAt) {
    }
}
