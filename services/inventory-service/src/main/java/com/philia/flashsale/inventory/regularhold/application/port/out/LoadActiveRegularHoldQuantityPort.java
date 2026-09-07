package com.philia.flashsale.inventory.regularhold.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Reads the durable active held quantity while the corresponding Inventory row is locked. */
public interface LoadActiveRegularHoldQuantityPort {
    long activeHeldQuantity(UUID inventoryItemId, Instant at);
}
