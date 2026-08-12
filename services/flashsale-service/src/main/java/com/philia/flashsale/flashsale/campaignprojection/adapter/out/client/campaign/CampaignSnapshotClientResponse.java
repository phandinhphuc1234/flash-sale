package com.philia.flashsale.flashsale.campaignprojection.adapter.out.client.campaign;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Remote DTO matching the raw, unwrapped Campaign snapshot response. */
public record CampaignSnapshotClientResponse(
        UUID campaignId,
        String campaignCode,
        String status,
        Instant startAt,
        Instant endAt,
        long aggregateVersion,
        CampaignSnapshotItemResponse item) {

    public record CampaignSnapshotItemResponse(
            UUID variantId,
            UUID inventoryAllocationId,
            String variantSku,
            BigDecimal campaignPrice,
            String currency,
            long allocatedQuantity,
            long purchaseLimitPerUser) {
    }
}
