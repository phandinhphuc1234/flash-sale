package com.philia.flashsale.campaign.campaign.application.command;

import java.util.Objects;
import java.util.UUID;

/** Input for one idempotent Campaign scheduling attempt. */
public record ScheduleCampaignCommand(
        UUID campaignId,
        long expectedVersion,
        String idempotencyKey,
        String callerService,
        String traceId) {

    public ScheduleCampaignCommand {
        Objects.requireNonNull(campaignId, "Campaign id is required");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("Campaign version must not be negative");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Trace id is required");
        }
        callerService = callerService == null ? "campaign-service" : callerService.trim();
    }
}
