package com.philia.flashsale.order.purchasesaga.application.result;

import java.util.Objects;
import java.util.UUID;

/** Stable result of one Inventory released/expired fact at the Order Saga boundary. */
public record RegularHoldOutcomeResult(Outcome outcome, UUID orderId, UUID sagaId, String conflictReason) {
    public enum Outcome { APPLIED, REPLAYED, MANUAL_REVIEW, STALE, CONFLICT }

    public RegularHoldOutcomeResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sagaId, "sagaId");
        if (outcome == Outcome.CONFLICT && (conflictReason == null || conflictReason.isBlank())) {
            throw new IllegalArgumentException("conflict outcome requires a reason");
        }
    }

    public static RegularHoldOutcomeResult applied(UUID orderId, UUID sagaId) {
        return new RegularHoldOutcomeResult(Outcome.APPLIED, orderId, sagaId, null);
    }

    public static RegularHoldOutcomeResult replayed(UUID orderId, UUID sagaId) {
        return new RegularHoldOutcomeResult(Outcome.REPLAYED, orderId, sagaId, null);
    }

    public static RegularHoldOutcomeResult manualReview(UUID orderId, UUID sagaId) {
        return new RegularHoldOutcomeResult(Outcome.MANUAL_REVIEW, orderId, sagaId, null);
    }

    public static RegularHoldOutcomeResult stale(UUID orderId, UUID sagaId) {
        return new RegularHoldOutcomeResult(Outcome.STALE, orderId, sagaId, "REGULAR_HOLD_RESULT_STALE");
    }

    public static RegularHoldOutcomeResult conflict(UUID orderId, UUID sagaId, String reason) {
        return new RegularHoldOutcomeResult(Outcome.CONFLICT, orderId, sagaId, reason);
    }
}
