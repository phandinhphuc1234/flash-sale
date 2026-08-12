package com.philia.flashsale.flashsale.reservation.application.command;

import java.util.Objects;
import java.util.UUID;

/** Use-case input for one authenticated shopper's Campaign reservation attempt. */
public record ReserveCampaignQuotaCommand(
        UUID campaignId,
        UUID variantId,
        UUID userId,
        long quantity,
        String idempotencyKey,
        java.time.Instant campaignEndsAt,
        String traceparent,
        String tracestate) {

    public ReserveCampaignQuotaCommand {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(campaignEndsAt, "campaignEndsAt");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must contain 1..128 characters");
        }
    }

    public ReserveCampaignQuotaCommand(UUID campaignId, UUID variantId, UUID userId, long quantity,
            String idempotencyKey, java.time.Instant campaignEndsAt) {
        this(campaignId, variantId, userId, quantity, idempotencyKey, campaignEndsAt, null, null);
    }
}
