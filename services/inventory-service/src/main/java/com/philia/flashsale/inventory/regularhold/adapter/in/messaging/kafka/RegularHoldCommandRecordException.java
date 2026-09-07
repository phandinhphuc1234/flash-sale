package com.philia.flashsale.inventory.regularhold.adapter.in.messaging.kafka;

/** Non-retryable malformed or untrusted command envelope. */
public class RegularHoldCommandRecordException extends RuntimeException {
    public RegularHoldCommandRecordException(String message) {
        super(message);
    }
}
