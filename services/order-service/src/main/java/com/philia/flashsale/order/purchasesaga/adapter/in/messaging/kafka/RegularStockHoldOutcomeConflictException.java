package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable identity/version conflict in a released/expired Inventory result. */
public final class RegularStockHoldOutcomeConflictException extends RuntimeException {
    public RegularStockHoldOutcomeConflictException(String message) {
        super(message);
    }
}
