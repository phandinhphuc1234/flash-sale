package com.philia.flashsale.order.regularpurchase.application.command;

import com.philia.flashsale.order.order.domain.valueobject.Money;
import java.util.Objects;
import java.util.UUID;

/** Authenticated Buy Now input after the web adapter has derived the shopper subject. */
public record BuyNowCheckoutCommand(
        UUID shopperId,
        String idempotencyKey,
        UUID variantId,
        long quantity,
        Money expectedUnitPrice,
        String currency,
        String traceId,
        String traceparent,
        String tracestate) {

    public BuyNowCheckoutCommand {
        Objects.requireNonNull(shopperId, "shopperId is required");
        Objects.requireNonNull(variantId, "variantId is required");
        Objects.requireNonNull(expectedUnitPrice, "expectedUnitPrice is required");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must contain one to 128 characters");
        }
        if (quantity < 1 || quantity > 10) {
            throw new IllegalArgumentException("quantity must be between one and ten");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be three uppercase letters");
        }
    }
}
