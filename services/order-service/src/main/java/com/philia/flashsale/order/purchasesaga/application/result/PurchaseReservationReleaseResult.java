package com.philia.flashsale.order.purchasesaga.application.result;

import java.util.Objects;
import java.util.UUID;

/** Outcome of the atomic Order/Saga release-result terminal transition. */
public record PurchaseReservationReleaseResult(Outcome outcome, UUID orderId, UUID sagaId, String conflictReason) {
    public enum Outcome { APPLIED, REPLAYED, CONFLICT }
    public PurchaseReservationReleaseResult {
        Objects.requireNonNull(outcome, "outcome"); Objects.requireNonNull(orderId, "orderId"); Objects.requireNonNull(sagaId, "sagaId");
        if (outcome == Outcome.CONFLICT && (conflictReason == null || conflictReason.isBlank())) throw new IllegalArgumentException("conflict reason is required");
    }
    public static PurchaseReservationReleaseResult applied(UUID orderId, UUID sagaId) { return new PurchaseReservationReleaseResult(Outcome.APPLIED, orderId, sagaId, null); }
    public static PurchaseReservationReleaseResult replayed(UUID orderId, UUID sagaId) { return new PurchaseReservationReleaseResult(Outcome.REPLAYED, orderId, sagaId, null); }
    public static PurchaseReservationReleaseResult conflict(UUID orderId, UUID sagaId, String reason) { return new PurchaseReservationReleaseResult(Outcome.CONFLICT, orderId, sagaId, reason); }
}
