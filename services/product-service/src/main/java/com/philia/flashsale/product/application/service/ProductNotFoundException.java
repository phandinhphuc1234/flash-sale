package com.philia.flashsale.product.application.service;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String slug) {
        super("Product was not found: " + slug);
    }
}
