package com.philia.flashsale.order.regularpurchase.application.port.in;

import com.philia.flashsale.order.regularpurchase.application.command.BuyNowCheckoutCommand;
import com.philia.flashsale.order.regularpurchase.application.result.RegularPurchaseCheckoutResult;

/** Accepts or safely replays one authenticated non-Cart regular purchase. */
public interface CheckoutBuyNowUseCase {
    RegularPurchaseCheckoutResult checkout(BuyNowCheckoutCommand command);
}
