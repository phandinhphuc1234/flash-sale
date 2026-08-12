package com.philia.flashsale.campaign.campaign.application.command;

import com.philia.flashsale.campaign.campaign.application.result.CampaignInventoryAllocation;
import com.philia.flashsale.campaign.campaign.application.result.ValidatedCampaignVariant;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Data passed to the short local transaction that freezes a scheduled Campaign. */
public record FinalizeCampaignSchedulingCommand(
        UUID campaignId,
        UUID operationId,
        long expectedVersion,
        ValidatedCampaignVariant product,
        CampaignInventoryAllocation allocation,
        String actor,
        String traceId,
        Instant now) {

    public FinalizeCampaignSchedulingCommand {
        Objects.requireNonNull(campaignId, "Campaign id is required");
        Objects.requireNonNull(operationId, "Schedule operation id is required");
        Objects.requireNonNull(product, "Product validation result is required");
        Objects.requireNonNull(allocation, "Inventory allocation is required");
        Objects.requireNonNull(now, "Schedule time is required");
        if (expectedVersion < 0 || actor == null || actor.isBlank()
                || traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("Invalid scheduling finalization command");
        }
    }
}
