package com.philia.flashsale.cart.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Point-in-time Cart intent captured for the Order checkout boundary. */
public record CartCheckoutSnapshotResult(
        UUID cartId,
        UUID ownerId,
        long cartVersion,
        Instant capturedAt,
        List<CartCheckoutSnapshotItem> items) {
    public CartCheckoutSnapshotResult {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
