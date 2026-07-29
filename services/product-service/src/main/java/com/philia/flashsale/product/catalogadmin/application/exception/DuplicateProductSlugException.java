package com.philia.flashsale.product.catalogadmin.application.exception;

public final class DuplicateProductSlugException extends RuntimeException {

    public DuplicateProductSlugException(String slug) {
        super("Product slug already exists: " + slug);
    }
}
