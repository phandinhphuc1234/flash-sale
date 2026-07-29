package com.philia.flashsale.product.catalogadmin.application.exception;

public final class DuplicateProductCodeException extends RuntimeException {

    public DuplicateProductCodeException(String code) {
        super("Product code already exists: " + code);
    }
}
