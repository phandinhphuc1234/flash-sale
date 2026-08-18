package com.philia.flashsale.payment.payment.adapter.in.web;

import com.philia.flashsale.payment.payment.adapter.in.web.response.PaymentDetailsResponse;
import com.philia.flashsale.payment.payment.application.model.query.PaymentDetailsResult;
import org.springframework.stereotype.Component;

/** Maps provider-independent application results to the safe public response. */
@Component
public final class PaymentQueryWebMapper {
    public PaymentDetailsResponse toResponse(PaymentDetailsResult result) {
        return new PaymentDetailsResponse(result.id(), result.orderId(), result.amount(), result.currency(),
                result.status(), result.paymentDeadline(), result.attemptsUsed(), result.failureReason(),
                result.createdAt(), result.updatedAt());
    }
}
