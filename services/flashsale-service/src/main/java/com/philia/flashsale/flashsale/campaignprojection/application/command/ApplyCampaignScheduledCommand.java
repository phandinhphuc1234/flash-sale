package com.philia.flashsale.flashsale.campaignprojection.application.command;

import com.philia.flashsale.flashsale.campaignprojection.domain.model.CampaignItemProjection;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Application input for replacing a Campaign's prepared scheduled snapshot. */
public record ApplyCampaignScheduledCommand(
        UUID campaignId,
        long aggregateVersion,
        Instant startsAt,
        Instant endsAt,
        CampaignItemProjection item,
        Instant occurredAt) {
    public ApplyCampaignScheduledCommand {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
