package com.philia.flashsale.order.purchasesaga.application.port.out;

import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentFailureResult;

/** Atomic Order-owned capability for applying one verified payment failure. */
public interface ApplyPaymentFailurePort {
    PaymentFailureResult apply(PaymentFailedCommand command);
}
