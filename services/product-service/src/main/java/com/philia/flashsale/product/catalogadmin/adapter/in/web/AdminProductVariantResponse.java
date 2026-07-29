package com.philia.flashsale.product.catalogadmin.adapter.in.web;

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
