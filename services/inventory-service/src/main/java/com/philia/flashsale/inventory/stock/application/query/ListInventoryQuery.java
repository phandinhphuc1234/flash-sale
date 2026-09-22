package com.philia.flashsale.inventory.stock.application.query;

/** Bounded page query for the admin inventory collection. */
public record ListInventoryQuery(int page, int size) {
    public static final int MAX_PAGE_SIZE = 100;

    public ListInventoryQuery {
        if (page < 0) {
            throw new IllegalArgumentException("Page number must be greater than or equal to 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("Page size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }
}
