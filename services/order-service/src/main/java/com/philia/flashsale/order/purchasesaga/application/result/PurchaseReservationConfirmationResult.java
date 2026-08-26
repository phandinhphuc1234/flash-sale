package com.philia.flashsale.order.purchasesaga.application.result;

import java.util.Objects;
import java.util.UUID;

/** Outcome of the atomic reservation-confirmation fact application. */
public record PurchaseReservationConfirmationResult(
        Outcome outcome, UUID orderId, UUID sagaId, String conflictReason) {

    public enum Outcome { APPLIED, REPLAYED, STALE, CONFLICT }

    public PurchaseReservationConfirmationResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(sagaId, "sagaId");
        if (outcome == Outcome.CONFLICT && (conflictReason == null || conflictReason.isBlank())) {
            throw new IllegalArgumentException("conflict reason is required");
        }
    }

    public static PurchaseReservationConfirmationResult applied(UUID orderId, UUID sagaId) {
        return new PurchaseReservationConfirmationResult(Outcome.APPLIED, orderId, sagaId, null);
    }

    public static PurchaseReservationConfirmationResult replayed(UUID orderId, UUID sagaId) {
        return new PurchaseReservationConfirmationResult(Outcome.REPLAYED, orderId, sagaId, null);
    }

    public static PurchaseReservationConfirmationResult stale(UUID orderId, UUID sagaId) {
        return new PurchaseReservationConfirmationResult(Outcome.STALE, orderId, sagaId, null);
    }

    public static PurchaseReservationConfirmationResult conflict(UUID orderId, UUID sagaId, String reason) {
        return new PurchaseReservationConfirmationResult(Outcome.CONFLICT, orderId, sagaId, reason);
    }
}
