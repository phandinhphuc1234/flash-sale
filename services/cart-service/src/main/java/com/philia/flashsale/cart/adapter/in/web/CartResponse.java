package com.philia.flashsale.cart.adapter.in.web;

import java.time.Instant;
import java.util.List;

/** Public Cart read response; Cart and owner identifiers remain internal. */
public record CartResponse(
        List<CartItemResponse> items,
        int distinctItemCount,
        int totalQuantity,
        long cartVersion,
        Instant updatedAt) {
    public CartResponse(List<CartItemResponse> items, int distinctItemCount, int totalQuantity, Instant updatedAt) {
        this(items, distinctItemCount, totalQuantity, 0, updatedAt);
    }
    public CartResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
