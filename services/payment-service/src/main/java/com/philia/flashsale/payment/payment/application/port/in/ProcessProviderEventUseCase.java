package com.philia.flashsale.payment.payment.application.port.in;

import com.philia.flashsale.payment.payment.application.model.webhook.ProviderEventProcessingResult;

/** Drives leased provider receipts to convergence without coupling the scheduler to persistence. */
public interface ProcessProviderEventUseCase {
    ProviderEventProcessingResult processBatch();
}
