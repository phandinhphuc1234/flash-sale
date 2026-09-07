package com.philia.flashsale.order.regularpurchase.domain.exception;

/** Raised when a regular-purchase intake aggregate would violate its durable workflow invariant. */
public class InvalidRegularPurchaseRequestException extends RuntimeException {

    public InvalidRegularPurchaseRequestException(String message) {
        super(message);
    }
}
