package com.philia.flashsale.order.regularpurchase.application.model;

import java.util.List;
import java.util.UUID;

/** Trusted Cart-owned intent returned by the internal snapshot client. */
public record CartCheckoutSnapshot(UUID cartId, UUID ownerId, long cartVersion,
        List<CartCheckoutSnapshotItem> items) {
    public CartCheckoutSnapshot {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record CartCheckoutSnapshotItem(UUID variantId, long quantity, long itemVersion) { }
}
