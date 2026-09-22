package com.philia.flashsale.inventory.stock.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ListInventoryQueryTest {

    @Test
    void acceptsZeroBasedPageAndBoundedSize() {
        var query = new ListInventoryQuery(0, ListInventoryQuery.MAX_PAGE_SIZE);

        assertEquals(0, query.page());
        assertEquals(ListInventoryQuery.MAX_PAGE_SIZE, query.size());
    }

    @Test
    void rejectsNegativePage() {
        assertThrows(IllegalArgumentException.class, () -> new ListInventoryQuery(-1, 20));
    }

    @Test
    void rejectsSizeOutsideThePublicBound() {
        assertThrows(IllegalArgumentException.class, () -> new ListInventoryQuery(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ListInventoryQuery(0, ListInventoryQuery.MAX_PAGE_SIZE + 1));
    }
}
