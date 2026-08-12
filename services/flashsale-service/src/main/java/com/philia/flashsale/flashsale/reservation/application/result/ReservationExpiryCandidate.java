package com.philia.flashsale.flashsale.reservation.application.result;

import java.time.Instant;
import java.util.UUID;

/** Durable data needed to arbitrate expiry, then issue an idempotent Redis release. */
public record ReservationExpiryCandidate(
        UUID purchaseRequestId,
        UUID reservationId,
        UUID campaignId,
        UUID variantId,
        UUID userId,
        long quantity,
        Instant expiresAt) {
}
