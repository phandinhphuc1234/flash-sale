package com.philia.flashsale.order.purchasesaga.application.port.in;

import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentFailureResult;

public interface ApplyPaymentFailureUseCase {
    PaymentFailureResult apply(PaymentFailedCommand command);
}
