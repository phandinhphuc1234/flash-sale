package com.philia.flashsale.order.regularpurchase.domain.model;

/** Durable checkpoints for a resumable regular-purchase intake. */
public enum RegularPurchaseRequestState {
    RECEIVED,
    SNAPSHOT_VALIDATED,
    PRODUCT_VALIDATED,
    HOLD_ACQUIRED,
    ACCEPTED,
    REJECTED;

    public boolean permitsBusinessRejection() {
        return this == RECEIVED || this == SNAPSHOT_VALIDATED || this == PRODUCT_VALIDATED;
    }
}
