package com.philia.flashsale.product.application.service;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String slug) {
        super("Category was not found: " + slug);
    }
}
