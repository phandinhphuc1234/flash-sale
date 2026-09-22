package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.util.UUID;

record AdminVariantDisplayResponse(
        UUID variantId,
        boolean found,
        UUID productId,
        String productName,
        String variantName,
        String sku,
        String basePrice,
        String currency,
        String productStatus,
        String variantStatus) {
}
