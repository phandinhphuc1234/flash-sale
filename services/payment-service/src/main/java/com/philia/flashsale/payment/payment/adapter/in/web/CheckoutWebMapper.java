package com.philia.flashsale.payment.payment.adapter.in.web;

import com.philia.flashsale.payment.payment.adapter.in.web.response.CheckoutSessionResponse;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutResult;
import org.springframework.stereotype.Component;

/** Maps application results to the Checkout-only public response. */
@Component
public final class CheckoutWebMapper {
    public CheckoutSessionResponse toResponse(StartCheckoutResult result) {
        return new CheckoutSessionResponse(result.paymentId(), result.paymentStatus(),
                result.checkoutUrl(), result.paymentDeadline());
    }
}
