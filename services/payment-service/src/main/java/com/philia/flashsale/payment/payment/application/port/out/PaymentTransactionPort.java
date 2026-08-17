package com.philia.flashsale.payment.payment.application.port.out;

import java.util.function.Supplier;

/** Transaction boundary abstraction; concrete transaction semantics stay in infrastructure. */
public interface PaymentTransactionPort {

    <T> T execute(Supplier<T> work);
}
