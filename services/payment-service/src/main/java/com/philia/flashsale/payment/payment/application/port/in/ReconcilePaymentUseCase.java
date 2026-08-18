package com.philia.flashsale.payment.payment.application.port.in;

import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentCommand;
import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentResult;
import com.philia.flashsale.payment.payment.application.model.recovery.RecoveryBatchResult;

/** Reconciles provider ambiguity and deadline work through provider truth. */
public interface ReconcilePaymentUseCase {

    ReconcilePaymentResult reconcile(ReconcilePaymentCommand command);

    RecoveryBatchResult reconcileDueWork();

    int scheduleDueDeadlines();
}
