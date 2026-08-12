package com.philia.flashsale.campaign.campaign.adapter.in.web.admin.response;

import java.time.Instant;
import java.util.UUID;

/** Direct HTTP representation of Campaign detail; it is intentionally not an API envelope. */
public record CampaignResponse(
        UUID id,
        String code,
        String name,
        String status,
        Instant startAt,
        Instant endAt,
        Instant scheduledAt,
        Instant activatedAt,
        Instant endedAt,
        long version,
        CampaignItemResponse item,
        Instant createdAt,
        Instant updatedAt) {
}
