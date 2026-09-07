package com.philia.flashsale.cart.adapter.in.messaging.kafka;

/** Permanent contract failure routed to the reconciliation DLT. */
public final class CartReconciliationRecordException extends RuntimeException {
    public CartReconciliationRecordException(String message) {
        super(message);
    }
}
