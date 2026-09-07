package com.philia.flashsale.order.purchasesaga.application.exception;

/** Non-retryable contradiction between an Inventory hold confirmation and Order-owned durable state. */
public final class InvalidRegularHoldConfirmationException extends RuntimeException {
    public InvalidRegularHoldConfirmationException(String message) {
        super(message);
    }
}
