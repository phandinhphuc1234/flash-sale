package com.philia.flashsale.campaign.campaign.adapter.in.web.admin.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** HTTP input for replacing the single editable item of a draft Campaign. */
public record ReplaceCampaignItemRequest(
        @NotNull(message = "variantId must not be null")
        UUID variantId,
        @NotNull(message = "campaignPrice must not be null")
        @DecimalMin(value = "0.0001", message = "campaignPrice must be greater than zero")
        BigDecimal campaignPrice,
        @Positive(message = "requestedQuantity must be greater than zero")
        long requestedQuantity,
        @Positive(message = "purchaseLimitPerUser must be greater than zero")
        long purchaseLimitPerUser) {
}
