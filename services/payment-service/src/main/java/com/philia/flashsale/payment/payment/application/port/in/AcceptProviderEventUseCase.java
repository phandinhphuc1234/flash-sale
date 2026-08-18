package com.philia.flashsale.payment.payment.application.port.in;

import com.philia.flashsale.payment.payment.application.model.webhook.ProviderEventAcceptanceResult;
import com.philia.flashsale.payment.payment.application.model.webhook.VerifiedProviderEvent;

/** Accepts only an already verified and allowlisted provider event. */
public interface AcceptProviderEventUseCase {
    ProviderEventAcceptanceResult accept(VerifiedProviderEvent event);
}
