package com.philia.flashsale.product.catalogadmin.application.exception;

public final class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException() { super("Referenced Category does not exist"); }
}
