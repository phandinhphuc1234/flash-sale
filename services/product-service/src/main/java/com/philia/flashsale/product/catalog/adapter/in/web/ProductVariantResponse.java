package com.philia.flashsale.product.catalog.adapter.in.web;

import java.util.UUID;

record ProductVariantResponse(
        UUID id,
        String sku,
        String name,
        String basePrice,
        String currency) {
}
