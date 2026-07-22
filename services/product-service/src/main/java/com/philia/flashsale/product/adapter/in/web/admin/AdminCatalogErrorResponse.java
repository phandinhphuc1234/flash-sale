package com.philia.flashsale.product.adapter.in.web.admin;

public record AdminCatalogErrorResponse(
        String code,
        String message,
        String traceId) {
}
