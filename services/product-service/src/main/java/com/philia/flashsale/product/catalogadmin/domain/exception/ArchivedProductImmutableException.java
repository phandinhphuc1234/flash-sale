package com.philia.flashsale.product.catalogadmin.domain.exception;

public final class ArchivedProductImmutableException extends CatalogDomainException {

    public ArchivedProductImmutableException(String message) {
        super("ARCHIVED_PRODUCT_IMMUTABLE", message);
    }
}
