package com.philia.flashsale.product.domain.model;

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
