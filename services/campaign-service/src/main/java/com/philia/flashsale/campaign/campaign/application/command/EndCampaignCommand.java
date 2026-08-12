package com.philia.flashsale.campaign.campaign.application.command;

import java.time.Instant;
import java.util.UUID;

/** Command used by lifecycle workers to close an active Campaign at its end boundary. */
public record EndCampaignCommand(
        UUID campaignId,
        long expectedVersion,
        String actor,
        Instant now,
        String traceId) {

    public EndCampaignCommand {
        if (campaignId == null || expectedVersion < 0 || actor == null || actor.isBlank()
                || now == null || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("A valid Campaign ending command is required");
        }
        actor = actor.trim();
        traceId = traceId.trim();
    }
}
