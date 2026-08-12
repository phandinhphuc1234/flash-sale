package com.philia.flashsale.product.catalogadmin.domain.exception;

public final class InvalidLifecycleTransitionException extends CatalogDomainException {

    public InvalidLifecycleTransitionException(String message) {
        super("INVALID_LIFECYCLE_TRANSITION", message);
    }
}
