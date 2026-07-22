package com.philia.flashsale.product.domain.model;

import java.util.UUID;

public record ProductMediaSummary(
        UUID id,
        String mediaType,
        String url,
        String altText,
        int sortOrder) {
}
