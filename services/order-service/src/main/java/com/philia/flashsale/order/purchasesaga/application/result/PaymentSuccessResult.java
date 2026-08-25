package com.philia.flashsale.order.purchasesaga.application.result;

import java.util.Objects;
import java.util.UUID;

/** Stable outcome of accepting or replaying a PaymentSucceeded fact. */
public record PaymentSuccessResult(Outcome outcome, UUID orderId, UUID sagaId,
        UUID confirmCommandId, String conflictReason) {

    public enum Outcome { APPLIED, REPLAYED, CONFLICT }

    public PaymentSuccessResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sagaId, "sagaId");
        if (outcome == Outcome.APPLIED && confirmCommandId == null) {
            throw new IllegalArgumentException("applied result requires confirm command identity");
        }
        if (outcome == Outcome.CONFLICT && (conflictReason == null || conflictReason.isBlank())) {
            throw new IllegalArgumentException("conflict result requires a reason");
        }
    }

    public static PaymentSuccessResult applied(UUID orderId, UUID sagaId, UUID commandId) {
        return new PaymentSuccessResult(Outcome.APPLIED, orderId, sagaId, commandId, null);
    }

    public static PaymentSuccessResult replayed(UUID orderId, UUID sagaId, UUID commandId) {
        return new PaymentSuccessResult(Outcome.REPLAYED, orderId, sagaId, commandId, null);
    }

    public static PaymentSuccessResult conflict(UUID orderId, UUID sagaId, String reason) {
        return new PaymentSuccessResult(Outcome.CONFLICT, orderId, sagaId, null, reason);
    }
}
