package com.philia.flashsale.payment.payment.adapter.in.scheduling;

import com.philia.flashsale.payment.payment.application.port.in.ReconcilePaymentUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Materializes deadline-expiry work; provider truth is still checked by the recovery worker. */
@Component
@ConditionalOnProperty(name = {"payment.acceptance.enabled", "payment.checkout.enabled",
        "payment.stripe.enabled", "payment.recovery.enabled"}, havingValue = "true")
public final class PaymentDeadlineJob {

    private final ReconcilePaymentUseCase reconciliation;

    public PaymentDeadlineJob(ReconcilePaymentUseCase reconciliation) {
        this.reconciliation = reconciliation;
    }

    @Scheduled(fixedDelayString = "${payment.recovery.poll-interval:1s}")
    public void scheduleDueDeadlines() {
        reconciliation.scheduleDueDeadlines();
    }
}
