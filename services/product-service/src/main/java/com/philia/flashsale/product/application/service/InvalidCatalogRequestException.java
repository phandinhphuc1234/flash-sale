package com.philia.flashsale.product.application.service;

public class InvalidCatalogRequestException extends RuntimeException {

    public InvalidCatalogRequestException(String message) {
        super(message);
    }
}
