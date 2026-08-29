package com.philia.flashsale.cart.application.usecase;

import com.philia.flashsale.cart.application.command.ClearCartCommand;
import com.philia.flashsale.cart.application.command.RemoveCartItemCommand;
import com.philia.flashsale.cart.application.command.SetCartItemCommand;
import com.philia.flashsale.cart.application.exception.CartVariantNotFoundException;
import com.philia.flashsale.cart.application.exception.CartVariantNotSellableException;
import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException;
import com.philia.flashsale.cart.application.port.in.ClearCartUseCase;
import com.philia.flashsale.cart.application.port.in.RemoveCartItemUseCase;
import com.philia.flashsale.cart.application.port.in.SetCartItemUseCase;
import com.philia.flashsale.cart.application.port.out.LoadProductDisplaysPort;
import com.philia.flashsale.cart.application.port.out.MaintainCartPort;
import com.philia.flashsale.cart.application.result.CartItemResult;
import com.philia.flashsale.cart.application.result.ProductDisplay;
import com.philia.flashsale.cart.domain.valueobject.CartQuantity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Orchestrates Cart mutation while keeping Product verification outside the Cart transaction. */
public final class MaintainCartService
        implements SetCartItemUseCase, RemoveCartItemUseCase, ClearCartUseCase {
    private final LoadProductDisplaysPort products;
    private final MaintainCartPort cart;
    private final Clock clock;

    public MaintainCartService(LoadProductDisplaysPort products, MaintainCartPort cart) {
        this(products, cart, Clock.systemUTC());
    }

    public MaintainCartService(LoadProductDisplaysPort products, MaintainCartPort cart, Clock clock) {
        this.products = Objects.requireNonNull(products, "products");
        this.cart = Objects.requireNonNull(cart, "cart");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CartItemResult set(SetCartItemCommand command) {
        Objects.requireNonNull(command, "command");
        CartQuantity quantity = CartQuantity.of(command.quantity());
        // The remote Product call deliberately happens before entering Cart persistence.
        ProductDisplay display = products.load(List.of(command.variantId()), command.traceId())
                .displays().stream().findFirst()
                .orElseThrow(() -> new ProductDisplayDependencyException(
                        ProductDisplayDependencyException.Failure.MALFORMED_RESPONSE));
        if (!display.found()) {
            throw new CartVariantNotFoundException();
        }
        if (!Boolean.TRUE.equals(display.sellable())) {
            throw new CartVariantNotSellableException();
        }
        var saved = cart.upsertItem(command.ownerId(), command.variantId(), quantity, Instant.now(clock));
        return CartItemResult.from(saved, display);
    }

    @Override
    public void remove(RemoveCartItemCommand command) {
        Objects.requireNonNull(command, "command");
        cart.removeItem(command.ownerId(), command.variantId(), Instant.now(clock));
    }

    @Override
    public void clear(ClearCartCommand command) {
        Objects.requireNonNull(command, "command");
        cart.clearItems(command.ownerId(), Instant.now(clock));
    }
}
