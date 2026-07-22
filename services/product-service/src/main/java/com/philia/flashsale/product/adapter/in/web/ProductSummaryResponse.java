package com.philia.flashsale.product.adapter.in.web;

import java.util.List;
import java.util.UUID;

record ProductSummaryResponse(
        UUID id,
        String code,
        String slug,
        String name,
        String shortDescription,
        List<ProductVariantResponse> variants) {
}
