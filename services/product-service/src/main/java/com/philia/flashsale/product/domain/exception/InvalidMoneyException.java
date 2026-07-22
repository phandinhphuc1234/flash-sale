package com.philia.flashsale.product.domain.exception;

public final class InvalidMoneyException extends CatalogDomainException {

    public InvalidMoneyException(String message) {
        super("INVALID_ADMIN_REQUEST", message);
    }
}
