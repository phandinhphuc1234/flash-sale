package com.philia.flashsale.product.catalogadmin.application.result;

import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;

public record AdminProductSummaryResult(
        UUID id,
        String code,
        String slug,
        String name,
        ProductStatus status,
        Instant publishedAt,
        long version) {
}
