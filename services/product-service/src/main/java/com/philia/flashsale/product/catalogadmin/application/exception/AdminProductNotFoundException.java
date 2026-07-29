package com.philia.flashsale.product.catalogadmin.application.exception;

import java.util.UUID;

public final class AdminProductNotFoundException extends RuntimeException {

    public AdminProductNotFoundException(UUID productId) {
        super("Product not found: " + productId);
    }
}
