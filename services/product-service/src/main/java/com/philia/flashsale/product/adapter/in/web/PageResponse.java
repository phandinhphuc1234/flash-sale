package com.philia.flashsale.product.adapter.in.web;

record PageResponse(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
