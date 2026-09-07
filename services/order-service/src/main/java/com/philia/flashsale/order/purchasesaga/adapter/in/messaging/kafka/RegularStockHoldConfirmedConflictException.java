package com.philia.flashsale.order.purchasesaga.adapter.in.messaging.kafka;

/** Non-retryable identity conflict for an already-consumed regular-hold confirmation. */
public final class RegularStockHoldConfirmedConflictException extends RuntimeException {
    public RegularStockHoldConfirmedConflictException(String message) {
        super(message);
    }
}
