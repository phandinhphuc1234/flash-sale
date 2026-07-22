package com.philia.flashsale.product.adapter.in.web.admin;

record AdminPageResponse(
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext) {
}
