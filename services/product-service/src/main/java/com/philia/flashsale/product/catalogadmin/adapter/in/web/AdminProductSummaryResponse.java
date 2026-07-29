package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

record AdminProductSummaryResponse(
        UUID id,
        String code,
        String slug,
        String name,
        String status,
        Instant publishedAt,
        long version) {
}
