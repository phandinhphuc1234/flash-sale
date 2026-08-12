package com.philia.flashsale.product.catalog.domain;

import java.util.UUID;

public record ProductMediaSummary(
        UUID id,
        String mediaType,
        String url,
        String altText,
        int sortOrder) {
}
