package com.philia.flashsale.payment.payment.adapter.in.scheduling;

import com.philia.flashsale.payment.payment.application.port.in.ProcessProviderEventUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls durable verified receipts; provider calls happen in the application service. */
@Component
@ConditionalOnProperty(name = {"payment.acceptance.enabled", "payment.checkout.enabled",
        "payment.stripe.enabled", "payment.webhook.processing.enabled"}, havingValue = "true")
public final class ProviderEventProcessingJob {
    private final ProcessProviderEventUseCase processor;

    public ProviderEventProcessingJob(ProcessProviderEventUseCase processor) {
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${payment.webhook.processing.poll-interval:1s}")
    public void process() {
        processor.processBatch();
    }
}
