package com.philia.flashsale.order.purchasesaga.application.result;

import java.util.Objects;
import java.util.UUID;

/** Stable outcome of accepting or replaying a terminal PaymentFailed fact. */
public record PaymentFailureResult(Outcome outcome, UUID orderId, UUID sagaId,
        UUID releaseCommandId, String desiredOrderStatus, String conflictReason) {
    public enum Outcome { APPLIED, REPLAYED, CONFLICT }
    public PaymentFailureResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sagaId, "sagaId");
        if (outcome != Outcome.CONFLICT && releaseCommandId == null) throw new IllegalArgumentException("release command identity required");
        if (outcome != Outcome.CONFLICT && (desiredOrderStatus == null || desiredOrderStatus.isBlank())) throw new IllegalArgumentException("desired status required");
        if (outcome == Outcome.CONFLICT && (conflictReason == null || conflictReason.isBlank())) throw new IllegalArgumentException("conflict reason required");
    }
    public static PaymentFailureResult applied(UUID orderId, UUID sagaId, UUID commandId, String status) { return new PaymentFailureResult(Outcome.APPLIED, orderId, sagaId, commandId, status, null); }
    public static PaymentFailureResult replayed(UUID orderId, UUID sagaId, UUID commandId, String status) { return new PaymentFailureResult(Outcome.REPLAYED, orderId, sagaId, commandId, status, null); }
    public static PaymentFailureResult conflict(UUID orderId, UUID sagaId, String reason) { return new PaymentFailureResult(Outcome.CONFLICT, orderId, sagaId, null, null, reason); }
}
