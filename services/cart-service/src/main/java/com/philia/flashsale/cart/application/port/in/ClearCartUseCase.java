package com.philia.flashsale.cart.application.port.in;

import com.philia.flashsale.cart.application.command.ClearCartCommand;

/** Public application capability for idempotently clearing one owner's Cart. */
public interface ClearCartUseCase {
    void clear(ClearCartCommand command);
}
