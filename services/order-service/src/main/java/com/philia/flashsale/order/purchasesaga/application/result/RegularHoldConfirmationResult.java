package com.philia.flashsale.order.purchasesaga.application.result;

import java.util.Objects;
import java.util.UUID;

/** Stable result of one Inventory regular-hold confirmation fact at the Order Saga boundary. */
public record RegularHoldConfirmationResult(Outcome outcome, UUID orderId, UUID sagaId, String conflictReason) {
    public enum Outcome { APPLIED, REPLAYED, STALE, CONFLICT }

    public RegularHoldConfirmationResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sagaId, "sagaId");
        if (outcome == Outcome.CONFLICT && (conflictReason == null || conflictReason.isBlank())) {
            throw new IllegalArgumentException("conflict outcome requires a reason");
        }
    }

    public static RegularHoldConfirmationResult applied(UUID orderId, UUID sagaId) {
        return new RegularHoldConfirmationResult(Outcome.APPLIED, orderId, sagaId, null);
    }

    public static RegularHoldConfirmationResult replayed(UUID orderId, UUID sagaId) {
        return new RegularHoldConfirmationResult(Outcome.REPLAYED, orderId, sagaId, null);
    }

    public static RegularHoldConfirmationResult stale(UUID orderId, UUID sagaId) {
        return new RegularHoldConfirmationResult(Outcome.STALE, orderId, sagaId, "REGULAR_HOLD_RESULT_STALE");
    }

    public static RegularHoldConfirmationResult conflict(UUID orderId, UUID sagaId, String reason) {
        return new RegularHoldConfirmationResult(Outcome.CONFLICT, orderId, sagaId, reason);
    }
}
