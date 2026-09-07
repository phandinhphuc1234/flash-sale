package com.philia.flashsale.order.regularpurchase.application.command;

import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.util.Objects;
import java.util.UUID;

public record CartCheckoutLine(UUID variantId, long quantity, long itemVersion, Money expectedUnitPrice,
        String currency) {
    public CartCheckoutLine {
        Objects.requireNonNull(variantId, "variantId is required");
        Objects.requireNonNull(expectedUnitPrice, "expectedUnitPrice is required");
        if (quantity < 1 || quantity > 10 || itemVersion <= 0) {
            throw new IllegalArgumentException("Cart quantity and itemVersion are invalid");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be three uppercase letters");
        }
    }
}
