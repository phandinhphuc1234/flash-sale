package com.philia.flashsale.payment.payment.application.port.in;

import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult;

/** Use case boundary for the Order-owned PaymentRequested.v1 command. */
public interface AcceptPaymentRequestUseCase {

    AcceptPaymentRequestResult accept(AcceptPaymentRequestCommand command);
}
