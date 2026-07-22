package com.philia.flashsale.product.adapter.in.web.admin;

import java.util.UUID;

record AdminProductVariantResponse(
        UUID id,
        String sku,
        String barcode,
        String name,
        String basePrice,
        String currency,
        String status,
        int sortOrder) {
}
