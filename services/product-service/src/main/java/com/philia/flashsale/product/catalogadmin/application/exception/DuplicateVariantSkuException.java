package com.philia.flashsale.product.catalogadmin.application.exception;

public final class DuplicateVariantSkuException extends RuntimeException {
    public DuplicateVariantSkuException(String sku) { super("Variant SKU already exists: " + sku); }
}
