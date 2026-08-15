package com.philia.flashsale.order.order.application.exception;

/** Application-level translation of a transient durable-storage failure. */
public class RetryableOrderPersistenceException extends RuntimeException {

    public RetryableOrderPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
