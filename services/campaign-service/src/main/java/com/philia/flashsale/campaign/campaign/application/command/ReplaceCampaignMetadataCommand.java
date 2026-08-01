package com.philia.flashsale.campaign.campaign.application.command;

import java.time.Instant;
import java.util.UUID;

/** Input for replacing all editable metadata of a draft Campaign. */
public record ReplaceCampaignMetadataCommand(
        UUID campaignId,
        long expectedVersion,
        String name,
        Instant startAt,
        Instant endAt) {
}
