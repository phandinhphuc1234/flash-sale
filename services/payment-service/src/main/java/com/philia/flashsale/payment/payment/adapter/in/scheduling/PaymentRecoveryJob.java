package com.philia.flashsale.payment.payment.adapter.in.scheduling;

import com.philia.flashsale.payment.observability.PaymentRecoveryObservability;
import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentResult;
import com.philia.flashsale.payment.payment.application.model.recovery.RecoveryBatchResult;
import com.philia.flashsale.payment.payment.application.port.in.ReconcilePaymentUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Claims and reconciles stale provider work at a bounded polling cadence. */
@Component
@ConditionalOnProperty(name = {"payment.acceptance.enabled", "payment.checkout.enabled",
        "payment.stripe.enabled", "payment.recovery.enabled"}, havingValue = "true")
public final class PaymentRecoveryJob {

    private final ReconcilePaymentUseCase reconciliation;
    private final PaymentRecoveryObservability observability;

    public PaymentRecoveryJob(ReconcilePaymentUseCase reconciliation) {
        this(reconciliation, PaymentRecoveryObservability.noop());
    }

    public PaymentRecoveryJob(ReconcilePaymentUseCase reconciliation,
            PaymentRecoveryObservability observability) {
        this.reconciliation = reconciliation;
        this.observability = observability;
    }

    @Scheduled(fixedDelayString = "${payment.recovery.poll-interval:1s}")
    public void reconcile() {
        RecoveryBatchResult result = reconciliation.reconcileDueWork();
        observability.recordQueueSize(result.claimed());
        if (result.deferred() > 0) {
            observability.recordOutcome("worker", ReconcilePaymentResult.Outcome.DEFERRED);
        }
        if (result.manualReview() > 0) {
            observability.recordOutcome("worker", ReconcilePaymentResult.Outcome.MANUAL_REVIEW);
        }
        if (result.converged() > 0) {
            observability.recordOutcome("worker", ReconcilePaymentResult.Outcome.CONVERGED);
        }
    }
}
