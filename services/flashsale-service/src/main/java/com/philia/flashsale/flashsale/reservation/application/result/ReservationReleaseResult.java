package com.philia.flashsale.flashsale.reservation.application.result;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Durable release outcome and the snapshot needed for Redis quota reconciliation. */
public record ReservationReleaseResult(UUID reservationId, UUID campaignId, UUID userId, UUID variantId,
        long quantity, UUID commandId, UUID resultEventId, Status status, String reason, Instant releasedAt) {
    public enum Status { RELEASED, ALREADY_RELEASED, EXPIRED, ALREADY_EXPIRED }
    public ReservationReleaseResult {
        Objects.requireNonNull(reservationId, "reservationId"); Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(userId, "userId"); Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(commandId, "commandId"); Objects.requireNonNull(resultEventId, "resultEventId");
        Objects.requireNonNull(status, "status"); Objects.requireNonNull(reason, "reason"); Objects.requireNonNull(releasedAt, "releasedAt");
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be positive");
    }
}
