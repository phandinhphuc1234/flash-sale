package com.philia.flashsale.cart.application.port.in;

import com.philia.flashsale.cart.application.query.GetCartQuery;
import com.philia.flashsale.cart.application.result.CartResult;

/** Reads one authenticated shopper's current Cart intent and Product display projection. */
public interface GetCartUseCase {
    CartResult get(GetCartQuery query);
}
