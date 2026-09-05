package com.philia.flashsale.cart.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Public Cart item response; Product fields are a current display projection only. */
public record CartItemResponse(
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
        Instant updatedAt) { }
