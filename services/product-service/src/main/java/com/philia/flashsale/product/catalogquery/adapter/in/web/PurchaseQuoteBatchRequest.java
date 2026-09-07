package com.philia.flashsale.product.catalogquery.adapter.in.web;

import com.philia.flashsale.product.catalogquery.application.PurchaseQuoteService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Transport validation for the bounded Order-only quote decision request. */
public record PurchaseQuoteBatchRequest(
        @NotEmpty(message = "variantIds is required")
        @Size(max = PurchaseQuoteService.MAXIMUM_VARIANT_LINES,
                message = "variantIds must contain at most 20 values")
        List<@NotNull(message = "variantIds cannot contain null") UUID> variantIds) {
}
