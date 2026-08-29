package com.philia.flashsale.cart.application.port.out;

import com.philia.flashsale.cart.application.result.CartItemState;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Instant;
import java.util.UUID;

/** Cart-owned persistence capability; implementations contain the transaction boundary. */
public interface MaintainCartPort {
    CartItemState upsertItem(UUID ownerId, UUID variantId, CartQuantity quantity, Instant now);

    void removeItem(UUID ownerId, UUID variantId, Instant now);
}
