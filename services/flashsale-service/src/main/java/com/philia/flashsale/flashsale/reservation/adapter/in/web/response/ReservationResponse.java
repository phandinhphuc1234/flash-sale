package com.philia.flashsale.flashsale.reservation.adapter.in.web.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public owner-scoped durable reservation representation. */
public record ReservationResponse(
        UUID purchaseRequestId,
        UUID reservationId,
        UUID campaignId,
        UUID variantId,
        String sku,
        BigDecimal unitPrice,
        String currency,
        long quantity,
        String status,
        Instant acceptedAt,
        Instant expiresAt) {
}
