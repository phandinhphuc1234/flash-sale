package com.philia.flashsale.inventory.stock.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.philia.flashsale.inventory.stock.domain.exception.InventoryDomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryItemTest {
    @Test
    void allocationCannotExceedAvailableStock() {
        InventoryItem item = InventoryItem.initialize(
                UUID.randomUUID(), UUID.randomUUID(), "SKU-1", Instant.now());
        item.increaseStock(5, Instant.now());
        item.allocate(3, Instant.now());

        assertEquals(2, item.availableQuantity());
        assertThrows(InventoryDomainException.class, () -> item.allocate(3, Instant.now()));
    }

    @Test
    void stockCannotBeReducedBelowAllocatedQuantity() {
        InventoryItem item = InventoryItem.initialize(
                UUID.randomUUID(), UUID.randomUUID(), "SKU-1", Instant.now());
        item.increaseStock(5, Instant.now());
        item.allocate(4, Instant.now());

        assertThrows(InventoryDomainException.class, () -> item.decreaseStock(2, Instant.now()));
    }
}
