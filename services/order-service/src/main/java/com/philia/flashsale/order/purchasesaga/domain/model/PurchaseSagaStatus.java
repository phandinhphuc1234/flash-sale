package com.philia.flashsale.order.purchasesaga.domain.model;

/** Durable lifecycle states for the Order-owned purchase saga. */
public enum PurchaseSagaStatus {
    PAYMENT_PENDING,
    CONFIRMING_RESERVATION,
    RELEASING_RESERVATION,
    COMPLETED,
    COMPENSATED,
    MANUAL_REVIEW
}
