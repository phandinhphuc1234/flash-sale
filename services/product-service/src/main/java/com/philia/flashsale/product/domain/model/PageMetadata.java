package com.philia.flashsale.product.domain.model;

public record PageMetadata(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
