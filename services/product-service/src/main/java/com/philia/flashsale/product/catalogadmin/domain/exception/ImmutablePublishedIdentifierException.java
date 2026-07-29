package com.philia.flashsale.product.catalogadmin.domain.exception;

public final class ImmutablePublishedIdentifierException extends CatalogDomainException {

    public ImmutablePublishedIdentifierException(String message) {
        super("IMMUTABLE_PUBLISHED_IDENTIFIER", message);
    }
}
