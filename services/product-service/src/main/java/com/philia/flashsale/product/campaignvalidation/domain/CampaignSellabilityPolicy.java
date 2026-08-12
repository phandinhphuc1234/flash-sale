package com.philia.flashsale.product.campaignvalidation.domain;

import java.time.Clock;
import java.time.OffsetDateTime;

/** Pure business policy for the Product-owned sellability boundary. */
public final class CampaignSellabilityPolicy {
    private final Clock clock;
    public CampaignSellabilityPolicy(Clock clock) { this.clock = clock; }

    public boolean isSellable(CampaignVariantSnapshot snapshot) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        return "ACTIVE".equals(snapshot.productStatus())
                && snapshot.publishedAt() != null
                && !snapshot.publishedAt().isAfter(now)
                && "ACTIVE".equals(snapshot.variantStatus())
                && snapshot.basePrice() != null
                && snapshot.basePrice().signum() > 0
                && "VND".equals(snapshot.currency());
    }
}
