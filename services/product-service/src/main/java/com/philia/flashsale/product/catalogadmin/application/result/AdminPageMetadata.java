package com.philia.flashsale.product.catalogadmin.application.result;

public record AdminPageMetadata(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
