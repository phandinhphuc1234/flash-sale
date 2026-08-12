package com.philia.flashsale.product.catalog.domain;

import java.util.List;
import java.util.UUID;

public record ProductSummary(
        UUID id,
        String code,
        String slug,
        String name,
        String shortDescription,
        List<ProductVariantSummary> variants) {
}
