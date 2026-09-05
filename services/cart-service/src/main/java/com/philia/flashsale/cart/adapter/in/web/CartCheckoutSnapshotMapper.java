package com.philia.flashsale.cart.adapter.in.web;

import com.philia.flashsale.cart.application.result.CartCheckoutSnapshotResult;
import org.springframework.stereotype.Component;

@Component
public final class CartCheckoutSnapshotMapper {
    public CartCheckoutSnapshotResponse toResponse(CartCheckoutSnapshotResult result) {
        return new CartCheckoutSnapshotResponse(result.cartId(), result.ownerId(), result.cartVersion(),
                result.capturedAt(), result.items().stream()
                        .map(item -> new CartCheckoutSnapshotResponse.Item(item.variantId(), item.quantity(),
                                item.itemVersion()))
                        .toList());
    }
}
