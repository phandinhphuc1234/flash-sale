package com.philia.flashsale.cart.application.usecase;

import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException;
import com.philia.flashsale.cart.application.port.in.GetCartUseCase;
import com.philia.flashsale.cart.application.port.out.LoadCartPort;
import com.philia.flashsale.cart.application.port.out.LoadProductDisplaysPort;
import com.philia.flashsale.cart.application.query.GetCartQuery;
import com.philia.flashsale.cart.application.result.CartItemResult;
import com.philia.flashsale.cart.application.result.CartItemState;
import com.philia.flashsale.cart.application.result.CartResult;
import com.philia.flashsale.cart.application.result.ProductDisplay;
import com.philia.flashsale.cart.application.result.ProductDisplayBatch;
import com.philia.flashsale.cart.domain.model.Cart;
import com.philia.flashsale.cart.domain.model.CartItem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Reads saved Cart intent, then enriches it with one fail-soft Product batch call. */
public final class GetCartService implements GetCartUseCase {
    private final LoadCartPort carts;
    private final LoadProductDisplaysPort products;

    public GetCartService(LoadCartPort carts, LoadProductDisplaysPort products) {
        this.carts = Objects.requireNonNull(carts, "carts");
        this.products = Objects.requireNonNull(products, "products");
    }

    @Override
    public CartResult get(GetCartQuery query) {
        Objects.requireNonNull(query, "query");
        Optional<Cart> loaded = carts.load(query.ownerId());
        if (loaded.isEmpty()) {
            return CartResult.empty();
        }

        Cart cart = loaded.orElseThrow();
        List<CartItem> savedItems = cart.items();
        if (savedItems.isEmpty()) {
            return new CartResult(List.of(), 0, 0, cart.updatedAt());
        }

        List<UUID> variantIds = savedItems.stream().map(CartItem::variantId).toList();
        ProductDisplayBatch batch = loadFailSoft(variantIds, query.traceId());
        Map<UUID, ProductDisplay> displays = byVariantId(batch);
        List<CartItemResult> resultItems = savedItems.stream()
                .map(item -> CartItemResult.from(
                        new CartItemState(
                                item.variantId(), item.quantity(), item.updatedAt()),
                        displays.getOrDefault(item.variantId(), ProductDisplay.unavailable(item.variantId()))))
                .toList();
        int totalQuantity = resultItems.stream().mapToInt(CartItemResult::quantity).sum();
        return new CartResult(resultItems, resultItems.size(), totalQuantity, cart.updatedAt());
    }

    private ProductDisplayBatch loadFailSoft(List<UUID> variantIds, String traceId) {
        try {
            ProductDisplayBatch loaded = products.load(variantIds, traceId);
            return loaded == null ? ProductDisplayBatch.unavailable(variantIds) : loaded;
        } catch (ProductDisplayDependencyException exception) {
            return ProductDisplayBatch.unavailable(variantIds);
        }
    }

    private Map<UUID, ProductDisplay> byVariantId(ProductDisplayBatch batch) {
        Map<UUID, ProductDisplay> result = new LinkedHashMap<>();
        if (batch == null || batch.displays() == null) {
            return result;
        }
        for (ProductDisplay display : batch.displays()) {
            if (display != null && display.variantId() != null) {
                result.putIfAbsent(display.variantId(), display);
            }
        }
        return result;
    }
}
