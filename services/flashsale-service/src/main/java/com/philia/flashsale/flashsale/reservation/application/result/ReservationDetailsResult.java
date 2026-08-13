package com.philia.flashsale.flashsale.reservation.application.result;

import com.philia.flashsale.flashsale.reservation.domain.model.ReservationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable durable reservation snapshot exposed by the owner-query use case. */
public record ReservationDetailsResult(
        UUID purchaseRequestId,
        UUID reservationId,
        UUID campaignId,
        UUID variantId,
        String skuSnapshot,
        BigDecimal unitPrice,
        String currency,
        long quantity,
        ReservationStatus status,
        Instant acceptedAt,
        Instant expiresAt) {
}
