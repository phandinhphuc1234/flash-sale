package com.philia.flashsale.product.catalog.domain;

public record PageMetadata(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
