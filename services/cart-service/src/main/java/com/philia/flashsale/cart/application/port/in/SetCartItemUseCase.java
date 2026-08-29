package com.philia.flashsale.cart.application.port.in;

import com.philia.flashsale.cart.application.command.SetCartItemCommand;
import com.philia.flashsale.cart.application.result.CartItemResult;

/** Public application capability for replacing one Cart item quantity. */
public interface SetCartItemUseCase {
    CartItemResult set(SetCartItemCommand command);
}
