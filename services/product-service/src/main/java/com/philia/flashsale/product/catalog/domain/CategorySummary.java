package com.philia.flashsale.product.catalog.domain;

import java.util.UUID;

public record CategorySummary(
        UUID id,
        UUID parentId,
        String slug,
        String name,
        int sortOrder) {
}
