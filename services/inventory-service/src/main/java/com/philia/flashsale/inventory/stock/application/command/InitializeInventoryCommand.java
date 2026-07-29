package com.philia.flashsale.inventory.stock.application.command;

import java.util.UUID;

/** Transport-independent intent to initialize one Product variant inventory item. */
public record InitializeInventoryCommand(
        UUID variantId,
        String skuSnapshot,
        long quantity,
        String reason
) {
}
