package com.philia.flashsale.cart.application.result;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Application result combining saved Cart intent with the Product verification used for a set. */
public record CartItemResult(
        UUID variantId,
        int quantity,
        boolean detailsAvailable,
        Boolean sellable,
        String unavailableReason,
        UUID productId,
        String productSlug,
        String productName,
        String variantName,
        String sku,
        BigDecimal basePrice,
        String currency,
        String primaryImageUrl,
        long itemVersion,
        Instant updatedAt) {

    public CartItemResult(UUID variantId, int quantity, boolean detailsAvailable, Boolean sellable,
            String unavailableReason, UUID productId, String productSlug, String productName, String variantName,
            String sku, BigDecimal basePrice, String currency, String primaryImageUrl, Instant updatedAt) {
        this(variantId, quantity, detailsAvailable, sellable, unavailableReason, productId, productSlug,
                productName, variantName, sku, basePrice, currency, primaryImageUrl, 1, updatedAt);
    }

    public static CartItemResult from(CartItemState state, ProductDisplay display) {
        boolean found = display != null && display.found();
        boolean dependencyUnavailable = display == null
                || (!display.found() && display.sellable() == null);
        String unavailable = dependencyUnavailable ? "PRODUCT_DETAILS_UNAVAILABLE"
                : !found ? "PRODUCT_NOT_FOUND"
                : Boolean.FALSE.equals(display.sellable()) ? "PRODUCT_NOT_SELLABLE" : null;
        return new CartItemResult(state.variantId(), state.quantity().value(), found,
                dependencyUnavailable ? null : display.sellable(), unavailable,
                found ? display.productId() : null,
                found ? display.productSlug() : null,
                found ? display.productName() : null,
                found ? display.variantName() : null,
                found ? display.sku() : null,
                found ? display.basePrice() : null,
                found ? display.currency() : null,
                found ? display.primaryImageUrl() : null, state.itemVersion(),
                state.updatedAt());
    }
}
