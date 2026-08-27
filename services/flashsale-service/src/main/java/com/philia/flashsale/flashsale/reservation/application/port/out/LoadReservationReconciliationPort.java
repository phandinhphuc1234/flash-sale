package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.result.ReservationReconciliationCandidate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable boundary for bounded Redis-finalization backlog claims and completion markers. */
public interface LoadReservationReconciliationPort {
    List<ReservationReconciliationCandidate> findPending(int batchSize);
    void markReconciled(UUID reservationId, Instant at);
}
