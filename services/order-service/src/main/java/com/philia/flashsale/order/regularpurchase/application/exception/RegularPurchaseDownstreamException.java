package com.philia.flashsale.order.regularpurchase.application.exception;

/** Sanitized result of an approved synchronous checkout dependency decision. */
public final class RegularPurchaseDownstreamException extends RuntimeException {

    public enum Failure {
        PRODUCT_QUOTE_REJECTED,
        PRODUCT_SERVICE_UNAVAILABLE,
        INVENTORY_INSUFFICIENT_STOCK,
        INVENTORY_ITEM_NOT_FOUND,
        INVENTORY_HOLD_CONFLICT,
        INVENTORY_HOLD_AMBIGUOUS,
        INVENTORY_SERVICE_UNAVAILABLE
    }

    private final Failure failure;

    public RegularPurchaseDownstreamException(Failure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public Failure failure() {
        return failure;
    }
}
