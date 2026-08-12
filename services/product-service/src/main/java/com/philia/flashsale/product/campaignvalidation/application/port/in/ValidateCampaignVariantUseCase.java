package com.philia.flashsale.product.campaignvalidation.application.port.in;

import com.philia.flashsale.product.campaignvalidation.application.usecase.CampaignValidationResult;
import java.util.UUID;

public interface ValidateCampaignVariantUseCase {
    CampaignValidationResult validate(UUID variantId);
}
