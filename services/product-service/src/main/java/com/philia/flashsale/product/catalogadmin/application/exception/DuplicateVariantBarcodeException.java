package com.philia.flashsale.product.catalogadmin.application.exception;

public final class DuplicateVariantBarcodeException extends RuntimeException {
    public DuplicateVariantBarcodeException(String barcode) { super("Variant barcode already exists: " + barcode); }
}
