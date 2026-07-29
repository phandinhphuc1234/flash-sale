package com.philia.flashsale.product.catalog.domain;

import java.util.UUID;

public record ProductCategorySummary(
        UUID id,
        String slug,
        String name,
        boolean primary,
        int sortOrder) {
}
