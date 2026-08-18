package com.philia.flashsale.payment.outbox.application.port;

import com.philia.flashsale.payment.outbox.application.model.PaymentOutboxEvent;
import java.time.Instant;
import java.util.List;

/** Outbox worker claim capability; PostgreSQL lease SQL remains adapter-local. */
public interface ClaimPaymentOutboxPort {

    List<PaymentOutboxEvent> claimBatch(
            Instant now, int batchSize, String leaseOwner, Instant leaseUntil);
}
