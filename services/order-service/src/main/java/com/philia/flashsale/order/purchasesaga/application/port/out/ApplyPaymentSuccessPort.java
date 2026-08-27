package com.philia.flashsale.order.purchasesaga.application.port.out;

import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;

/** Atomic Order-owned capability for applying one verified payment success. */
public interface ApplyPaymentSuccessPort {
    PaymentSuccessResult apply(PaymentSucceededCommand command);
}
