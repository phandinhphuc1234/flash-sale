package com.philia.flashsale.flashsale.reservation.application.command;

import java.util.Objects;
import java.util.UUID;

/** Application input for an authenticated public reservation submission. */
public record SubmitReservationCommand(
        UUID campaignId,
        UUID variantId,
        UUID userId,
        long quantity,
        String idempotencyKey,
        String traceparent,
        String tracestate) {

    public SubmitReservationCommand {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..128 characters");
        }
    }
}
