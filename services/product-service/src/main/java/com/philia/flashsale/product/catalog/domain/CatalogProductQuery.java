package com.philia.flashsale.product.catalog.domain;

import java.math.BigDecimal;

public record CatalogProductQuery(
        String text,
        String categorySlug,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        CatalogSort sort) {
}
