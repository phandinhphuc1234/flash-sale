package com.philia.flashsale.inventory.allocation.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

/** HTTP request used to allocate variant stock to a campaign. */
public record AllocateRequest(
        @NotNull UUID requestId,
        @NotNull UUID campaignId,
        @NotNull UUID variantId,
        @Positive long quantity,
        @NotBlank String reason
) {
}
