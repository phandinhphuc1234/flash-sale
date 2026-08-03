package com.philia.flashsale.campaign.campaign.adapter.in.web.internal.response;

import com.philia.flashsale.campaign.campaign.application.result.CampaignSnapshotResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Direct JSON response for the private Flash Sale snapshot contract. */
public record CampaignSnapshotResponse(
        UUID campaignId,
        String campaignCode,
        String status,
        Instant startAt,
        Instant endAt,
        long aggregateVersion,
        CampaignSnapshotItemResponse item) {

    public static CampaignSnapshotResponse from(CampaignSnapshotResult result) {
        CampaignSnapshotResult.CampaignSnapshotItemResult item = result.item();
        return new CampaignSnapshotResponse(
                result.campaignId(),
                result.campaignCode(),
                result.status().name(),
                result.startAt(),
                result.endAt(),
                result.aggregateVersion(),
                new CampaignSnapshotItemResponse(
                        item.variantId(),
                        item.inventoryAllocationId(),
                        item.variantSku(),
                        item.campaignPrice(),
                        item.currency(),
                        item.allocatedQuantity(),
                        item.purchaseLimitPerUser()));
    }

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
