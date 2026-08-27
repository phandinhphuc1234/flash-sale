package com.philia.flashsale.flashsale.reservation.application.result;

import java.util.UUID;

/** Framework-free snapshot of a durable finalization still awaiting Redis projection repair. */
public record ReservationReconciliationCandidate(
        UUID reservationId, UUID campaignId, UUID userId, UUID variantId,
        long quantity, Status status) {
    public enum Status { CONFIRMED, RELEASED, EXPIRED }

    public ReservationReconciliationCandidate {
        if (reservationId == null || campaignId == null || userId == null || variantId == null) {
            throw new NullPointerException("reservation identities are required");
        }
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
        if (status == null) throw new NullPointerException("status");
    }
}
