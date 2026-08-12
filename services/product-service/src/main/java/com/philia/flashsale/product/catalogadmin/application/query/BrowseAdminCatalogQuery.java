package com.philia.flashsale.product.catalogadmin.application.query;

import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;

public record BrowseAdminCatalogQuery(
        ProductStatus status,
        String q,
        int page,
        int size) {

    public BrowseAdminCatalogQuery {
        q = q == null || q.isBlank() ? null : q.trim();
    }
}
