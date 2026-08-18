package com.philia.flashsale.payment.outbox.adapter.in.scheduling;

import com.philia.flashsale.payment.outbox.application.service.PaymentOutboxPublicationService;
import org.springframework.scheduling.annotation.Scheduled;

/** Framework scheduler that triggers the application-owned outbox relay. */
public final class PaymentOutboxPublisherJob {

    private final PaymentOutboxPublicationService publicationService;

    public PaymentOutboxPublisherJob(PaymentOutboxPublicationService publicationService) {
        this.publicationService = publicationService;
    }

    @Scheduled(fixedDelayString = "${payment.kafka.outbox-poll-interval:500ms}")
    public void publishDue() {
        publicationService.publishDue();
    }
}
