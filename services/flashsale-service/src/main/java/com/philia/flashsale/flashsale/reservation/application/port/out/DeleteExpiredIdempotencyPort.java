package com.philia.flashsale.flashsale.reservation.application.port.out;

import java.time.Instant;

/** Deletes only expired replay identities; purchase, reservation, and outbox audit rows are untouched. */
public interface DeleteExpiredIdempotencyPort {
    int deleteExpired(Instant now, int batchSize);
}
