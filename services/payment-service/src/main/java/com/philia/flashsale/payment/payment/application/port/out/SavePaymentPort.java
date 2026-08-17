package com.philia.flashsale.payment.payment.application.port.out;

import com.philia.flashsale.payment.payment.domain.model.Payment;

/** Aggregate persistence capability owned by the application core. */
public interface SavePaymentPort {

    Payment save(Payment payment);
}
