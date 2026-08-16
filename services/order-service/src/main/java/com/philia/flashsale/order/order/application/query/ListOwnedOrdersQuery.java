package com.philia.flashsale.order.order.application.query;

import com.philia.flashsale.order.order.application.exception.InvalidOrderQueryException;
import java.util.Objects;
import java.util.UUID;

/** Bounded owner-scoped page request. */
public record ListOwnedOrdersQuery(UUID ownerId, int page, int size) {
    public ListOwnedOrdersQuery {
        Objects.requireNonNull(ownerId, "ownerId");
        if (page < 0) {
            throw new InvalidOrderQueryException("page must be zero or positive", "page");
        }
        if (size <= 0) {
            throw new InvalidOrderQueryException("size must be greater than zero", "size");
        }
    }
}
