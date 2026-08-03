package com.philia.flashsale.campaign.campaign.application.command;

import java.util.Objects;
import java.util.UUID;

/** Caller-owned allocation command whose request ID remains stable across recovery attempts. */
public record AllocateCampaignInventoryCommand(
        UUID requestId,
        UUID campaignId,
        UUID variantId,
        long quantity) {

    public AllocateCampaignInventoryCommand {
        Objects.requireNonNull(requestId, "requestId is required");
        Objects.requireNonNull(campaignId, "campaignId is required");
        Objects.requireNonNull(variantId, "variantId is required");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
    }
}
