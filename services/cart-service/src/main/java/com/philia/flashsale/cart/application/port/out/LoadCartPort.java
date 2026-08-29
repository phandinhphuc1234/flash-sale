package com.philia.flashsale.cart.application.port.out;

import com.philia.flashsale.cart.domain.model.Cart;
import java.util.Optional;
import java.util.UUID;

/** Cart-owned read capability; it never accepts a caller-selected Cart id. */
public interface LoadCartPort {
    Optional<Cart> load(UUID ownerId);
}
