package com.philia.flashsale.order.regularpurchase.domain.model;

import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.regularpurchase.domain.exception.InvalidRegularPurchaseRequestException;
import java.util.Objects;
import java.util.UUID;

/** One shopper-confirmed regular-purchase line before Product supplies an authoritative quote. */
public record RegularPurchaseLine(UUID variantId, long quantity, Money expectedUnitPrice, String currency,
        Long cartItemVersion) {

    public RegularPurchaseLine {
        Objects.requireNonNull(variantId, "variantId");
        Objects.requireNonNull(expectedUnitPrice, "expectedUnitPrice");
        if (quantity < 1 || quantity > RegularPurchaseRequest.MAX_LINE_QUANTITY) {
            throw new InvalidRegularPurchaseRequestException("regular purchase quantity must be between one and ten");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new InvalidRegularPurchaseRequestException("currency must be three uppercase ASCII letters");
        }
        if (cartItemVersion != null && cartItemVersion <= 0) {
            throw new InvalidRegularPurchaseRequestException("Cart item version must be positive");
        }
    }
}
