package com.philia.flashsale.product.catalog.adapter.in.web;

import java.util.List;
import java.util.UUID;

record ProductDetailResponse(
        UUID id,
        String code,
        String slug,
        String name,
        String shortDescription,
        String description,
        List<ProductVariantResponse> variants,
        List<ProductCategoryResponse> categories,
        List<ProductMediaResponse> media) {
}
