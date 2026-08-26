package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.result.ReservationReconciliationCandidate;

/** Applies one durable finalization to the Redis projection idempotently. */
public interface ReconcileReservationProjectionPort {
    void reconcile(ReservationReconciliationCandidate candidate);
}
