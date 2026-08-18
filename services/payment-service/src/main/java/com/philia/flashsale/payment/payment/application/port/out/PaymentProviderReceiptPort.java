package com.philia.flashsale.payment.payment.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable verified provider-event receipt and worker-claim capability. */
public interface PaymentProviderReceiptPort {

    Optional<Receipt> findByProviderEventId(String providerEventId);

    Receipt receive(Receipt receipt);

    List<Receipt> claimReceiptBatch(Instant now, int batchSize, String leaseOwner, Instant leaseUntil);

    void markProcessed(UUID receiptId, Instant processedAt);

    default void markIgnored(UUID receiptId, Instant processedAt) {
        markProcessed(receiptId, processedAt);
    }

    default void reschedule(UUID receiptId, String errorCode, Instant nextAttemptAt) {
        // Optional for in-memory adapters; the PostgreSQL adapter persists the lease/backoff state.
    }

    default void markReceiptManualReview(UUID receiptId, String errorCode, Instant observedAt) {
        // Optional for in-memory adapters; the PostgreSQL adapter persists manual-review evidence.
    }

    record Receipt(UUID id, String providerEventId, String providerEventType, boolean liveMode,
            String providerApiVersion, String providerObjectId, UUID paymentId, UUID attemptId,
            UUID orderId,
            Instant providerCreatedAt, Instant verifiedAt, String processingStatus,
            String lastErrorCode, int attemptCount, Instant nextAttemptAt) {

        public Receipt(UUID id, String providerEventId, String providerEventType, boolean liveMode,
                String providerObjectId, UUID paymentId, UUID attemptId, UUID orderId,
                Instant providerCreatedAt, Instant verifiedAt, String processingStatus,
                String lastErrorCode, int attemptCount, Instant nextAttemptAt) {
            this(id, providerEventId, providerEventType, liveMode, null, providerObjectId, paymentId,
                    attemptId, orderId, providerCreatedAt, verifiedAt, processingStatus, lastErrorCode,
                    attemptCount, nextAttemptAt);
        }
    }
}
