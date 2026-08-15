package com.philia.flashsale.order.order.domain.exception;

/** Raised when an accepted-purchase snapshot cannot form a valid Order aggregate. */
public class InvalidOrderException extends RuntimeException {

    public InvalidOrderException(String message) {
        super(message);
    }
}
