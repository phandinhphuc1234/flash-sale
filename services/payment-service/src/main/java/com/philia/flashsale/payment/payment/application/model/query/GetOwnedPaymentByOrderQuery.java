package com.philia.flashsale.payment.payment.application.model.query;

import java.util.Objects;
import java.util.UUID;

/** Owner-scoped lookup by the durable Order identity. */
public record GetOwnedPaymentByOrderQuery(UUID orderId, UUID ownerId) {
    public GetOwnedPaymentByOrderQuery {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
