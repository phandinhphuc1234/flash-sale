package com.philia.flashsale.product.domain.exception;

public class CatalogDomainException extends RuntimeException {

    private final String code;

    public CatalogDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
