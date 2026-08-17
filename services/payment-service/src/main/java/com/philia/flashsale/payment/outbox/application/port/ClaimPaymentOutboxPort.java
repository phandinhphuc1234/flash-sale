package com.philia.flashsale.payment.outbox.application.port;

import java.time.Instant;
import java.util.List;

/** Outbox worker claim capability; PostgreSQL lease SQL remains adapter-local. */
public interface ClaimPaymentOutboxPort {

    List<SavePaymentOutboxPort.OutboxRecord> claimBatch(
            Instant now, int batchSize, String leaseOwner, Instant leaseUntil);
}
