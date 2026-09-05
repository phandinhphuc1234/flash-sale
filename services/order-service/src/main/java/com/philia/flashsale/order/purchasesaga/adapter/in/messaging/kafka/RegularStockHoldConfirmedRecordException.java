package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable malformed or unsupported Inventory regular-hold confirmation record. */
public final class RegularStockHoldConfirmedRecordException extends RuntimeException {
    public RegularStockHoldConfirmedRecordException(String message) {
        super(message);
    }
}
