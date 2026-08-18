package com.philia.flashsale.payment.payment.adapter.in.web.response;

import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

/** Minimal owner response; Checkout URL is intentionally not part of durable Payment views. */
public record CheckoutSessionResponse(UUID paymentId, PaymentStatus status, String checkoutUrl,
        Instant paymentDeadline) {
}
