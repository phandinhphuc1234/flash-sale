package com.philia.flashsale.order.purchasesaga.domain.exception;

/** Raised when a purchase saga violates an approved lifecycle invariant. */
public class InvalidPurchaseSagaException extends RuntimeException {

    public InvalidPurchaseSagaException(String message) {
        super(message);
    }
}
