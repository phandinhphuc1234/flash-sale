package com.philia.flashsale.product.catalogadmin.application.query;

import java.util.UUID;

public record ViewAdminProductQuery(UUID productId) {

    public ViewAdminProductQuery {
        if (productId == null) {
            throw new IllegalArgumentException("Product ID is required");
        }
    }
}
