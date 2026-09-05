package com.philia.flashsale.cart.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartCheckoutSnapshotResponse(
        UUID cartId,
        UUID ownerId,
        long cartVersion,
        Instant capturedAt,
        List<Item> items) {
    public CartCheckoutSnapshotResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(UUID variantId, int quantity, long itemVersion) { }
}
