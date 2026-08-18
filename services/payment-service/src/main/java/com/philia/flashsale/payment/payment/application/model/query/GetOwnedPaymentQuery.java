package com.philia.flashsale.payment.payment.application.model.query;

import java.util.Objects;
import java.util.UUID;

/** Owner-scoped lookup by the durable Payment identity. */
public record GetOwnedPaymentQuery(UUID paymentId, UUID ownerId) {
    public GetOwnedPaymentQuery {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
