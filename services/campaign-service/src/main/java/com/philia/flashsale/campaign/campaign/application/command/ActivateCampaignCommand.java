package com.philia.flashsale.campaign.campaign.application.command;

import java.time.Instant;
import java.util.UUID;

/** Command for automatic activation or authorized manual recovery activation. */
public record ActivateCampaignCommand(
        UUID campaignId,
        long expectedVersion,
        String actor,
        Instant now,
        String traceId,
        boolean manualRecovery) {

    public ActivateCampaignCommand {
        if (campaignId == null || expectedVersion < 0 || actor == null || actor.isBlank()
                || now == null || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("A valid Campaign activation command is required");
        }
        actor = actor.trim();
        traceId = traceId.trim();
    }
}
