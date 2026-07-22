package com.philia.flashsale.product.adapter.in.web;

import java.util.UUID;

record ProductVariantResponse(
        UUID id,
        String sku,
        String name,
        String basePrice,
        String currency) {
}
