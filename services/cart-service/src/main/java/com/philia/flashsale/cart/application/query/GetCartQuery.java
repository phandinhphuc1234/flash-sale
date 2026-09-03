package com.philia.flashsale.cart.application.query;

import java.util.Objects;
import java.util.UUID;

/** Owner-scoped Cart read intent; the owner comes from the validated JWT at the web boundary. */
public record GetCartQuery(UUID ownerId, String traceId) {
    public GetCartQuery {
        Objects.requireNonNull(ownerId, "ownerId");
        traceId = traceId == null || traceId.isBlank() ? null : traceId.trim();
    }
}
