package com.philia.flashsale.cart.application.command;

import java.util.Objects;
import java.util.UUID;

/** Transport-independent intent to remove one variant from the owner's Cart. */
public record RemoveCartItemCommand(UUID ownerId, UUID variantId) {
    public RemoveCartItemCommand {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(variantId, "variantId");
    }
}
