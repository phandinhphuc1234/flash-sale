package com.philia.flashsale.product.catalogadmin.application.result;

import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;

public record CreateProductDraftResult(
        UUID id,
        ProductStatus status,
        long version,
        boolean replayed) {
}
