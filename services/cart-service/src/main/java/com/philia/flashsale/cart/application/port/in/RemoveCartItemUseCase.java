package com.philia.flashsale.cart.application.port.in;

import com.philia.flashsale.cart.application.command.RemoveCartItemCommand;

/** Public application capability for idempotently removing one Cart item. */
public interface RemoveCartItemUseCase {
    void remove(RemoveCartItemCommand command);
}
