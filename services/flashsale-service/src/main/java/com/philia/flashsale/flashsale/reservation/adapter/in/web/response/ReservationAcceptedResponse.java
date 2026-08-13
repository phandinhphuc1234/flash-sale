package com.philia.flashsale.flashsale.reservation.adapter.in.web.response;

import java.time.Instant;
import java.util.UUID;

/** Public response snapshot for a durably accepted reservation. */
public record ReservationAcceptedResponse(
        UUID purchaseRequestId,
        UUID reservationId,
        UUID campaignId,
        UUID variantId,
        long quantity,
        String status,
        Instant expiresAt) {
}
