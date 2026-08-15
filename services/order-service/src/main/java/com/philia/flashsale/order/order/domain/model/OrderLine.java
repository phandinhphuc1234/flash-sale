package com.philia.flashsale.order.order.domain.model;

import com.philia.flashsale.order.order.domain.exception.InvalidOrderException;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.util.Objects;
import java.util.UUID;

/** Immutable one-item commercial snapshot contained by an Order. */
public record OrderLine(UUID id, UUID variantId, long quantity, Money unitPrice, Money lineAmount) {

    public OrderLine {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(unitPrice, "unitPrice");
        Objects.requireNonNull(lineAmount, "lineAmount");
        if (quantity <= 0) {
            throw new InvalidOrderException("order line quantity must be positive");
        }
        if (!lineAmount.equals(unitPrice.multiply(quantity))) {
            throw new InvalidOrderException("order line amount must equal unit price multiplied by quantity");
        }
    }

    public static OrderLine create(UUID id, UUID variantId, long quantity, Money unitPrice) {
        return new OrderLine(id, variantId, quantity, unitPrice, unitPrice.multiply(quantity));
    }
}
