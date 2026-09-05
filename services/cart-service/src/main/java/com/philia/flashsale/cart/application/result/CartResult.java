package com.philia.flashsale.cart.application.result;

import java.time.Instant;
import java.util.List;

/** Publicly mappable Cart read result containing saved intent and current Product details. */
public record CartResult(
        List<CartItemResult> items,
        int distinctItemCount,
        int totalQuantity,
        long cartVersion,
        Instant updatedAt) {

    public CartResult(List<CartItemResult> items, int distinctItemCount, int totalQuantity, Instant updatedAt) {
        this(items, distinctItemCount, totalQuantity, 0, updatedAt);
    }

    public CartResult {
        items = items == null ? List.of() : List.copyOf(items);
        if (distinctItemCount < 0 || totalQuantity < 0) {
            throw new IllegalArgumentException("Cart counts cannot be negative");
        }
        if (cartVersion < 0) {
            throw new IllegalArgumentException("cartVersion cannot be negative");
        }
        if (distinctItemCount != items.size()) {
            throw new IllegalArgumentException("distinctItemCount must match items");
        }
        if (items.stream().mapToInt(CartItemResult::quantity).sum() != totalQuantity) {
            throw new IllegalArgumentException("totalQuantity must match item quantities");
        }
    }

    public static CartResult empty() {
        return new CartResult(List.of(), 0, 0, 0, null);
    }
}
