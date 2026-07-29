package com.philia.flashsale.product.catalogadmin.domain.exception;

public final class ProductOwnershipMismatchException extends CatalogDomainException {

    public ProductOwnershipMismatchException(String message) {
        super("PRODUCT_OWNERSHIP_MISMATCH", message);
    }
}
