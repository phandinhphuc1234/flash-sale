package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.port.out.LoadReservationReconciliationPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationProjectionPort;
import java.time.Clock;
import java.util.Objects;

/** Runs a bounded, restart-safe durable-to-Redis reconciliation pass. */
public final class ReservationReconciliationService {
    private final LoadReservationReconciliationPort backlog;
    private final ReconcileReservationProjectionPort projection;
    private final Clock clock;

    public ReservationReconciliationService(LoadReservationReconciliationPort backlog,
            ReconcileReservationProjectionPort projection, Clock clock) {
        this.backlog = Objects.requireNonNull(backlog, "backlog");
        this.projection = Objects.requireNonNull(projection, "projection");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int reconcileBatch(int batchSize) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("batchSize must be in 1..100");
        int completed = 0;
        for (var candidate : backlog.findPending(batchSize)) {
            try {
                projection.reconcile(candidate);
                backlog.markReconciled(candidate.reservationId(), clock.instant());
                completed++;
            } catch (RuntimeException ignored) {
                // A failed projection remains unmarked and is retried by the next bounded pass.
            }
        }
        return completed;
    }
}
