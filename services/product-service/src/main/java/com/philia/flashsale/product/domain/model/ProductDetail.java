package com.philia.flashsale.product.domain.model;

import java.util.List;
import java.util.UUID;

public record ProductDetail(
        UUID id,
        String code,
        String slug,
        String name,
        String shortDescription,
        String description,
        List<ProductVariantSummary> variants,
        List<ProductCategorySummary> categories,
        List<ProductMediaSummary> media) {
}
