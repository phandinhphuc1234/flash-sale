package com.philia.flashsale.order.regularpurchase.adapter.out.client.cart;

import java.util.List;
import java.util.UUID;

final class CartCheckoutSnapshotWireModels {
    private CartCheckoutSnapshotWireModels() { }

    record Request(UUID shopperId) { }
    record Response(UUID cartId, UUID ownerId, long cartVersion, java.time.Instant capturedAt, List<Item> items) { }
    record Item(UUID variantId, long quantity, long itemVersion) { }
}
