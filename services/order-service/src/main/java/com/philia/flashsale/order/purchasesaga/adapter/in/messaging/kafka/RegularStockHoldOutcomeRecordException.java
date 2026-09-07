package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable malformed or unsupported released/expired Inventory result record. */
public final class RegularStockHoldOutcomeRecordException extends RuntimeException {
    public RegularStockHoldOutcomeRecordException(String message) {
        super(message);
    }
}
