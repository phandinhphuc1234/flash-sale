package com.philia.flashsale.flashsale.campaignprojection.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Application input for activating an already prepared Campaign projection. */
public record ApplyCampaignActivatedCommand(
        UUID campaignId,
        long aggregateVersion,
        Instant startsAt,
        Instant endsAt,
        Instant occurredAt) {
    public ApplyCampaignActivatedCommand {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
