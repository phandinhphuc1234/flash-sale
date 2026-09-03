package com.philia.flashsale.cart.application.command;

import java.util.Objects;
import java.util.UUID;

/** Transport-independent intent to clear every item belonging to one owner. */
public record ClearCartCommand(UUID ownerId) {
    public ClearCartCommand {
        Objects.requireNonNull(ownerId, "ownerId");
    }
}
