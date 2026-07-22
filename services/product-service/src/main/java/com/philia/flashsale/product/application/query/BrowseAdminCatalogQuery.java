package com.philia.flashsale.product.application.query;

import com.philia.flashsale.product.domain.model.ProductStatus;

public record BrowseAdminCatalogQuery(
        ProductStatus status,
        String q,
        int page,
        int size) {

    public BrowseAdminCatalogQuery {
        q = q == null || q.isBlank() ? null : q.trim();
    }
}
