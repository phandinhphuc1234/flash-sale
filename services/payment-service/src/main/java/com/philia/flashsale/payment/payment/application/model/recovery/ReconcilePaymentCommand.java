package com.philia.flashsale.payment.payment.application.model.recovery;

import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import java.time.Instant;
import java.util.Objects;

/** Transport-independent command for one claimed durable recovery item. */
public record ReconcilePaymentCommand(PaymentRecoveryWorkPort.Work work, Instant observedAt) {

    public ReconcilePaymentCommand {
        Objects.requireNonNull(work, "work");
        Objects.requireNonNull(observedAt, "observedAt");
    }
}
