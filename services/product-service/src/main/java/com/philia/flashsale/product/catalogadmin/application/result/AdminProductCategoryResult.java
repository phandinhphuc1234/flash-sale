package com.philia.flashsale.product.catalogadmin.application.result;

import java.util.UUID;

public record AdminProductCategoryResult(
        UUID id,
        boolean primary,
        int sortOrder) {
}
