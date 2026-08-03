package com.philia.flashsale.campaign.campaign.application.port.out;

import com.philia.flashsale.campaign.campaign.application.result.ValidatedCampaignVariant;
import java.util.UUID;

/** Retrieves authoritative Product eligibility and price data needed for scheduling. */
public interface ValidateCampaignVariantPort {

    ValidatedCampaignVariant validate(UUID variantId, String traceId);
}
