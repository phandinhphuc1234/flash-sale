package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record AdminProductDetailResponse(
        UUID id,
        String code,
        String slug,
        String name,
        String shortDescription,
        String description,
        String status,
        Instant publishedAt,
        long version,
        List<AdminProductVariantResponse> variants,
        List<AdminProductCategoryResponse> categories,
        List<AdminProductMediaResponse> media) {
}
