package com.philia.flashsale.inventory.regularhold.application.exception;

/** Non-retryable Kafka identity or impossible-state rejection, routed to the consumer-specific DLT. */
public class RegularHoldCommandConflictException extends RuntimeException {
    public RegularHoldCommandConflictException(String message) {
        super(message);
    }
}
