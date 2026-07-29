package com.philia.flashsale.inventory.allocation.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.philia.flashsale.inventory.allocation.domain.exception.AllocationDomainException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CampaignStockAllocationTest {
    @Test
    void reconciliationRequiresSoldAndReturnedToEqualAllocation() {
        CampaignStockAllocation allocation = CampaignStockAllocation.active(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 10, Instant.now());

        assertThrows(AllocationDomainException.class,
                () -> allocation.reconcile(6, 3, Instant.now()));
        allocation.reconcile(6, 4, Instant.now());

        assertEquals(AllocationStatus.RECONCILED, allocation.status());
    }
}
