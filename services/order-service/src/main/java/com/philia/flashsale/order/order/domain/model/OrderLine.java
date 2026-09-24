package com.philia.flashsale.order.order.domain.model;

import com.philia.flashsale.order.order.domain.exception.InvalidOrderException;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.util.Objects;
import java.util.UUID;

/** Immutable one-item commercial snapshot contained by an Order. */
public record OrderLine(UUID id, UUID variantId, long quantity, Money unitPrice, Money lineAmount,
        String productName, String variantName) {

    public OrderLine(UUID id, UUID variantId, long quantity, Money unitPrice, Money lineAmount) {
        this(id, variantId, quantity, unitPrice, lineAmount, null, null);
    }

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
        productName = normalizeName(productName, "productName");
        variantName = normalizeName(variantName, "variantName");
    }

    public static OrderLine create(UUID id, UUID variantId, long quantity, Money unitPrice) {
        return create(id, variantId, quantity, unitPrice, null, null);
    }

    public static OrderLine create(UUID id, UUID variantId, long quantity, Money unitPrice,
            String productName, String variantName) {
        return new OrderLine(id, variantId, quantity, unitPrice, unitPrice.multiply(quantity),
                productName, variantName);
    }

    private static String normalizeName(String value, String field) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > 255) {
            throw new InvalidOrderException(field + " must contain at most 255 characters");
        }
        return normalized;
    }
}
