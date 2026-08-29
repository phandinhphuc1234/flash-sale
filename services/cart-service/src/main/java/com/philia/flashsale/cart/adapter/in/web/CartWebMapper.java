package com.philia.flashsale.cart.adapter.in.web;

import com.philia.flashsale.cart.application.result.CartItemResult;
import com.philia.flashsale.cart.application.result.CartResult;
import org.springframework.stereotype.Component;

/** Explicit mapper between application results and the public Cart HTTP representation. */
@Component
public final class CartWebMapper {
    public CartItemResponse toResponse(CartItemResult result) {
        return new CartItemResponse(result.variantId(), result.quantity(), result.detailsAvailable(),
                result.sellable(), result.unavailableReason(), result.productId(), result.productSlug(),
                result.productName(), result.variantName(), result.sku(), result.basePrice(), result.currency(),
                result.primaryImageUrl(), result.updatedAt());
    }

    public CartResponse toResponse(CartResult result) {
        return new CartResponse(result.items().stream().map(this::toResponse).toList(),
                result.distinctItemCount(), result.totalQuantity(), result.updatedAt());
    }
}
