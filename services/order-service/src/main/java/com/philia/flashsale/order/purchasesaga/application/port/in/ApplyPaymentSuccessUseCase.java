package com.philia.flashsale.order.purchasesaga.application.port.in;

import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;

/** Use-case boundary for verified PaymentSucceeded facts. */
public interface ApplyPaymentSuccessUseCase {
    PaymentSuccessResult apply(PaymentSucceededCommand command);
}
