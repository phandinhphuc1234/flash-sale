package com.philia.flashsale.order.order.application.result;

import java.util.Objects;
import java.util.UUID;

/** Outcome of the atomic Order creation capability. */
public record OrderCreationResult(Outcome outcome, UUID orderId, UUID outboxEventId, String fingerprint,
        String conflictReason, UUID purchaseSagaId, UUID paymentRequestedOutboxEventId) {

    /** Backward-compatible result constructor for existing Order-only callers. */
    public OrderCreationResult(Outcome outcome, UUID orderId, UUID outboxEventId, String fingerprint,
            String conflictReason) {
        this(outcome, orderId, outboxEventId, fingerprint, conflictReason, null, null);
    }

    public enum Outcome { CREATED, EVENT_REPLAYED, BUSINESS_REPLAYED, CONFLICT }

    public OrderCreationResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(fingerprint, "fingerprint");
        if (outcome == Outcome.CREATED && outboxEventId == null) {
            throw new IllegalArgumentException("created result requires outbox identity");
        }
        if (outcome == Outcome.CONFLICT && (conflictReason == null || conflictReason.isBlank())) {
            throw new IllegalArgumentException("conflict result requires a reason");
        }
    }

    public static OrderCreationResult created(UUID orderId, UUID outboxEventId, String fingerprint) {
        return new OrderCreationResult(Outcome.CREATED, orderId, outboxEventId, fingerprint, null);
    }

    public static OrderCreationResult createdWithSaga(UUID orderId, UUID outboxEventId,
            UUID purchaseSagaId, UUID paymentRequestedOutboxEventId, String fingerprint) {
        return new OrderCreationResult(Outcome.CREATED, orderId, outboxEventId, fingerprint, null,
                purchaseSagaId, paymentRequestedOutboxEventId);
    }

    public static OrderCreationResult eventReplayed(UUID orderId, String fingerprint) {
        return new OrderCreationResult(Outcome.EVENT_REPLAYED, orderId, null, fingerprint, null);
    }

    public static OrderCreationResult businessReplayed(UUID orderId, String fingerprint) {
        return new OrderCreationResult(Outcome.BUSINESS_REPLAYED, orderId, null, fingerprint, null);
    }

    public static OrderCreationResult conflict(UUID orderId, String fingerprint, String reason) {
        return new OrderCreationResult(Outcome.CONFLICT, orderId, null, fingerprint, reason);
    }
}
