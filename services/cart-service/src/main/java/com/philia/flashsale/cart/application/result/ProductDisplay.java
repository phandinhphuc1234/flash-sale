package com.philia.flashsale.cart.application.result;

import java.math.BigDecimal;
import java.util.UUID;

/** A trusted Product-owned display result used by Cart application services. */
public record ProductDisplay(
        UUID variantId,
        boolean found,
        Boolean sellable,
        UUID productId,
        String productSlug,
        String productName,
        String variantName,
        String sku,
        BigDecimal basePrice,
        String currency,
        String primaryImageUrl) {

    public static ProductDisplay missing(UUID variantId) {
        return new ProductDisplay(variantId, false, false, null, null, null, null, null, null, null, null);
    }

    /** Represents a dependency outage: Product-owned fields are deliberately unavailable. */
    public static ProductDisplay unavailable(UUID variantId) {
        return new ProductDisplay(variantId, false, null, null, null, null, null, null, null, null, null);
    }
}
