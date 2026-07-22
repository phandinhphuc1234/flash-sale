package com.philia.flashsale.product.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.domain.model.ProductStatus;

public record AdminProductDetailResult(
        UUID id,
        String code,
        String slug,
        String name,
        String shortDescription,
        String description,
        ProductStatus status,
        Instant publishedAt,
        long version,
        List<AdminProductVariantResult> variants,
        List<AdminProductCategoryResult> categories,
        List<AdminProductMediaResult> media) {

    public AdminProductDetailResult {
        variants = List.copyOf(variants);
        categories = List.copyOf(categories);
        media = List.copyOf(media);
    }
}
