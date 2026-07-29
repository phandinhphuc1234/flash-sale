package com.philia.flashsale.product.catalogadmin.adapter.in.web;

public record AdminCatalogErrorResponse(
        String code,
        String message,
        String traceId) {
}
