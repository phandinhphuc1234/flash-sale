package com.philia.flashsale.cart.application.usecase;

import com.philia.flashsale.cart.application.exception.CartEmptyException;
import com.philia.flashsale.cart.application.exception.CartNotFoundException;
import com.philia.flashsale.cart.application.port.in.GetCartCheckoutSnapshotUseCase;
import com.philia.flashsale.cart.application.port.out.LoadCartCheckoutSnapshotPort;
import com.philia.flashsale.cart.application.query.GetCartCheckoutSnapshotQuery;
import com.philia.flashsale.cart.application.result.CartCheckoutSnapshotResult;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Captures only Cart-owned intent for Order; it never calls Product or exposes prices. */
public final class GetCartCheckoutSnapshotService implements GetCartCheckoutSnapshotUseCase {
    private final LoadCartCheckoutSnapshotPort carts;
    private final Clock clock;

    public GetCartCheckoutSnapshotService(LoadCartCheckoutSnapshotPort carts) {
        this(carts, Clock.systemUTC());
    }

    public GetCartCheckoutSnapshotService(LoadCartCheckoutSnapshotPort carts, Clock clock) {
        this.carts = Objects.requireNonNull(carts, "carts");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CartCheckoutSnapshotResult get(GetCartCheckoutSnapshotQuery query) {
        Objects.requireNonNull(query, "query");
        if (query.shopperId() == null) {
            throw new IllegalArgumentException("shopperId is required");
        }
        CartCheckoutSnapshotResult result = carts.loadCheckoutSnapshot(query.shopperId())
                .orElseThrow(CartNotFoundException::new);
        if (result.items().isEmpty()) {
            throw new CartEmptyException();
        }
        return new CartCheckoutSnapshotResult(result.cartId(), result.ownerId(), result.cartVersion(),
                Instant.now(clock), result.items());
    }
}
