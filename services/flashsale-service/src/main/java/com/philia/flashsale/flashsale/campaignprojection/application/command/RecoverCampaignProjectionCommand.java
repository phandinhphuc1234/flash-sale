package com.philia.flashsale.flashsale.campaignprojection.application.command;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Application input for a control-plane Campaign snapshot recovery attempt. */
public record RecoverCampaignProjectionCommand(UUID campaignId, Instant requestedAt) {
    public RecoverCampaignProjectionCommand {
        Objects.requireNonNull(campaignId, "campaignId");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
