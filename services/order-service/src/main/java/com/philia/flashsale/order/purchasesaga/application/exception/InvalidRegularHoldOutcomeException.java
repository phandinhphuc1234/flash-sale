package com.philia.flashsale.order.purchasesaga.application.exception;

/** Permanent identity/state conflict while applying an Inventory regular-hold outcome. */
public final class InvalidRegularHoldOutcomeException extends RuntimeException {
    public InvalidRegularHoldOutcomeException(String message) {
        super(message);
    }
}
