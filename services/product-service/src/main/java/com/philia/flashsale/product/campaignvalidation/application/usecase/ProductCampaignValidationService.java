package com.philia.flashsale.product.campaignvalidation.application.usecase;

import com.philia.flashsale.product.campaignvalidation.application.port.in.ValidateCampaignVariantUseCase;
import com.philia.flashsale.product.campaignvalidation.application.port.out.LoadCampaignVariantPort;
import com.philia.flashsale.product.campaignvalidation.domain.CampaignSellabilityPolicy;
import com.philia.flashsale.product.campaignvalidation.domain.CampaignVariantSnapshot;
import java.time.Clock;
import java.util.UUID;

/** Orchestrates Product truth lookup and the campaign sellability policy. */
public class ProductCampaignValidationService implements ValidateCampaignVariantUseCase {
    private final LoadCampaignVariantPort variants;
    private final CampaignSellabilityPolicy policy;
    public ProductCampaignValidationService(LoadCampaignVariantPort variants, Clock clock) {
        this.variants = variants; this.policy = new CampaignSellabilityPolicy(clock);
    }
    @Override
    public CampaignValidationResult validate(UUID variantId) {
        CampaignVariantSnapshot snapshot = variants.load(variantId)
                .orElseThrow(() -> new ProductVariantNotFoundException(variantId));
        if (!policy.isSellable(snapshot)) {
            throw new ProductVariantNotSellableException(variantId);
        }
        return new CampaignValidationResult(snapshot.productId(), snapshot.variantId(), snapshot.sku(),
                snapshot.productStatus(), snapshot.variantStatus(), true, snapshot.basePrice(), snapshot.currency());
    }
}
