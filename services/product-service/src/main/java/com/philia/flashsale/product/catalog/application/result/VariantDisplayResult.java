package com.philia.flashsale.product.catalog.application.result;

import java.math.BigDecimal;
import java.util.UUID;

/** Product-owned display data; Cart may show it but never persists or owns it. */
public record VariantDisplayResult(
        UUID variantId,
        boolean found,
        boolean sellable,
        UUID productId,
        String productSlug,
        String productName,
        String variantName,
        String sku,
        BigDecimal basePrice,
        String currency,
        String primaryImageUrl) {

    public static VariantDisplayResult missing(UUID variantId) {
        return new VariantDisplayResult(
                variantId, false, false, null, null, null, null, null, null, null, null);
    }
}
