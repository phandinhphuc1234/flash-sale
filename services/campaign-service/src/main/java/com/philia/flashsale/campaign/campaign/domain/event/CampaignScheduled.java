package com.philia.flashsale.campaign.campaign.domain.event;

import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Canonical internal source for the versioned CampaignScheduled lifecycle notification. */
public record CampaignScheduled(
        UUID eventId,
        String eventType,
        int eventVersion,
        String aggregateType,
        UUID aggregateId,
        long aggregateVersion,
        Instant occurredAt,
        Data data) {

    public static CampaignScheduled of(UUID eventId, Campaign campaign, Instant occurredAt) {
        CampaignItem item = campaign.item();
        return new CampaignScheduled(
                eventId, "CampaignScheduled", 1, "Campaign", campaign.id(), campaign.version(),
                occurredAt,
                new Data(campaign.id(), campaign.code(), campaign.startAt(), campaign.endAt(),
                        item.productId(), item.variantId(), item.inventoryAllocationId(),
                        item.variantSkuSnapshot(), item.campaignPrice().amount(),
                        item.campaignPrice().currency(), item.allocatedQuantity(),
                        item.purchaseLimitPerUser()));
    }

    public record Data(
            UUID campaignId,
            String campaignCode,
            Instant startAt,
            Instant endAt,
            UUID productId,
            UUID variantId,
            UUID inventoryAllocationId,
            String variantSku,
            BigDecimal campaignPrice,
            String currency,
            long allocatedQuantity,
            long purchaseLimitPerUser) {
    }
}
