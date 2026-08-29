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
        Instant updatedAt) {

    public static CartItemResult from(CartItemState state, ProductDisplay display) {
        boolean details = display != null;
        boolean found = details && display.found();
        String unavailable = !found ? "PRODUCT_NOT_FOUND"
                : Boolean.FALSE.equals(display.sellable()) ? "PRODUCT_NOT_SELLABLE" : null;
        return new CartItemResult(state.variantId(), state.quantity().value(), details,
                details ? display.sellable() : null, unavailable,
                found ? display.productId() : null,
                found ? display.productSlug() : null,
                found ? display.productName() : null,
                found ? display.variantName() : null,
                found ? display.sku() : null,
                found ? display.basePrice() : null,
                found ? display.currency() : null,
                found ? display.primaryImageUrl() : null,
                state.updatedAt());
    }
}
