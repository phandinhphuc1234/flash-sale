package com.philia.flashsale.payment.payment.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable command receipt capability for idempotent PaymentRequested processing. */
public interface PaymentCommandInboxPort {

    Optional<Receipt> findByEventId(UUID eventId);

    Optional<Receipt> findByOrderId(UUID orderId);

    Receipt receive(UUID eventId, String eventType, int eventVersion, UUID orderId,
            String payloadFingerprint, Instant receivedAt);

    void markProcessed(UUID eventId, UUID paymentId, Instant processedAt);

    void markConflicted(UUID eventId, Instant observedAt);

    record Receipt(UUID eventId, String eventType, int eventVersion, UUID orderId,
            String payloadFingerprint, UUID paymentId, String processingStatus,
            Instant receivedAt, Instant processedAt) {
    }
}
