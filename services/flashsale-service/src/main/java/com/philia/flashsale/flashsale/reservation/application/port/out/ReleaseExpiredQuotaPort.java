package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.result.ReservationExpiryCandidate;

/** Idempotent hot-path compensation invoked only after a terminal durable outcome. */
public interface ReleaseExpiredQuotaPort {
    void release(ReservationExpiryCandidate candidate);
}
