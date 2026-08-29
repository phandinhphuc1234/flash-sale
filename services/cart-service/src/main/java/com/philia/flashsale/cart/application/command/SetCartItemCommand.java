package com.philia.flashsale.cart.application.command;

import java.util.Objects;
import java.util.UUID;

/** Transport-independent intent to replace one cart item's absolute quantity. */
public record SetCartItemCommand(UUID ownerId, UUID variantId, int quantity, String traceId) {
    public SetCartItemCommand {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(variantId, "variantId");
    }
}
