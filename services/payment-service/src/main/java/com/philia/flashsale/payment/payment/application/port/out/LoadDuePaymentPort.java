package com.philia.flashsale.payment.payment.application.port.out;

import com.philia.flashsale.payment.payment.domain.model.Payment;
import java.time.Instant;
import java.util.List;

/** Loads deadline-eligible aggregates without exposing persistence types to reconciliation. */
public interface LoadDuePaymentPort {

    List<Payment> findDeadlineCandidates(Instant now, int batchSize);
}
