package com.philia.flashsale.product.campaignvalidation.application.port.out;

import com.philia.flashsale.product.campaignvalidation.domain.CampaignVariantSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface LoadCampaignVariantPort {
    Optional<CampaignVariantSnapshot> load(UUID variantId);
}
