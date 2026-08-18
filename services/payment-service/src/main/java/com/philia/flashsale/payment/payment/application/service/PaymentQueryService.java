package com.philia.flashsale.payment.payment.application.service;

import com.philia.flashsale.payment.payment.application.exception.PaymentNotFoundException;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentByOrderQuery;
import com.philia.flashsale.payment.payment.application.model.query.GetOwnedPaymentQuery;
import com.philia.flashsale.payment.payment.application.model.query.PaymentDetailsResult;
import com.philia.flashsale.payment.payment.application.port.in.GetOwnedPaymentUseCase;
import com.philia.flashsale.payment.payment.application.port.out.LoadOwnedPaymentPort;
import java.util.Objects;

/** Reads durable owner state without consulting Stripe, Kafka, or browser sessions. */
public final class PaymentQueryService implements GetOwnedPaymentUseCase {
    private final LoadOwnedPaymentPort payments;

    public PaymentQueryService(LoadOwnedPaymentPort payments) {
        this.payments = Objects.requireNonNull(payments, "payments");
    }

    @Override
    public PaymentDetailsResult get(GetOwnedPaymentQuery query) {
        Objects.requireNonNull(query, "query");
        return payments.load(query).orElseThrow(PaymentNotFoundException::new);
    }

    @Override
    public PaymentDetailsResult getByOrder(GetOwnedPaymentByOrderQuery query) {
        Objects.requireNonNull(query, "query");
        return payments.loadByOrder(query).orElseThrow(PaymentNotFoundException::new);
    }
}
