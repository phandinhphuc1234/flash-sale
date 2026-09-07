package com.philia.flashsale.order.regularpurchase.application.port.in;

import com.philia.flashsale.order.regularpurchase.application.command.CartCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.result.RegularPurchaseCheckoutResult;

public interface CheckoutCartUseCase {
    RegularPurchaseCheckoutResult checkout(CartCheckoutCommand command);
}
