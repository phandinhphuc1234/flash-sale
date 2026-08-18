package com.philia.flashsale.payment.payment.application.port.in;

import com.philia.flashsale.payment.payment.application.model.StartCheckoutCommand;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutResult;

/** Starts or resumes one durable hosted Checkout attempt for an authenticated owner. */
public interface StartCheckoutUseCase {
    StartCheckoutResult start(StartCheckoutCommand command);
}
