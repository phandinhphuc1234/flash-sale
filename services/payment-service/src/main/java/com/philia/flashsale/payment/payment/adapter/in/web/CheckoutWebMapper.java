package com.philia.flashsale.payment.payment.adapter.in.web;

import com.philia.flashsale.payment.payment.adapter.in.web.response.CheckoutSessionResponse;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutResult;

/** Maps application results to the Checkout-only public response. */
public final class CheckoutWebMapper {
    public CheckoutSessionResponse toResponse(StartCheckoutResult result) {
        return new CheckoutSessionResponse(result.paymentId(), result.paymentStatus(),
                result.checkoutUrl(), result.paymentDeadline());
    }
}
