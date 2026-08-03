package com.philia.flashsale.campaign.campaign.application.result;

import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Stable application read model for rebuilding Flash Sale runtime state. */
public record CampaignSnapshotResult(
        UUID campaignId,
        String campaignCode,
        CampaignStatus status,
        Instant startAt,
        Instant endAt,
        long aggregateVersion,
        CampaignSnapshotItemResult item) {

    public static CampaignSnapshotResult from(Campaign campaign) {
        return new CampaignSnapshotResult(
                campaign.id(),
                campaign.code(),
                campaign.status(),
                campaign.startAt(),
                campaign.endAt(),
                campaign.version(),
                CampaignSnapshotItemResult.from(campaign.item()));
    }

    /** Snapshot fields intentionally exclude audit, operation, and outbox metadata. */
    public record CampaignSnapshotItemResult(
            UUID variantId,
            UUID inventoryAllocationId,
            String variantSku,
            BigDecimal campaignPrice,
            String currency,
            long allocatedQuantity,
            long purchaseLimitPerUser) {

        static CampaignSnapshotItemResult from(CampaignItem item) {
            return new CampaignSnapshotItemResult(
                    item.variantId(),
                    item.inventoryAllocationId(),
                    item.variantSkuSnapshot(),
                    item.campaignPrice().amount(),
                    item.campaignPrice().currency(),
                    item.allocatedQuantity(),
                    item.purchaseLimitPerUser());
        }
    }
}
