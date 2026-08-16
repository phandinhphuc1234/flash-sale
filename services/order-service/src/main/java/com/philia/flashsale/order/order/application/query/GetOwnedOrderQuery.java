package com.philia.flashsale.order.order.application.query;

import java.util.Objects;
import java.util.UUID;

/** Owner-scoped detail lookup; the owner identity always comes from the trusted JWT subject. */
public record GetOwnedOrderQuery(UUID orderId, UUID ownerId) {
    public GetOwnedOrderQuery {
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
