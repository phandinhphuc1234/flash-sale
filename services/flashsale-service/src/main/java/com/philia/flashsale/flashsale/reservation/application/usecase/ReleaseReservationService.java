package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.in.ReleaseReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationReleasePort;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationReleasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult;
import java.util.Objects;

/** Coordinates durable-first release and retryable Redis quota reconciliation. */
public final class ReleaseReservationService implements ReleaseReservationUseCase {
    private final PersistReservationReleasePort persistence;
    private final ReconcileReservationReleasePort redis;
    public ReleaseReservationService(PersistReservationReleasePort persistence, ReconcileReservationReleasePort redis) { this.persistence = Objects.requireNonNull(persistence, "persistence"); this.redis = Objects.requireNonNull(redis, "redis"); }
    @Override public ReservationReleaseResult release(ReleaseReservationCommand command) {
        ReservationReleaseResult result = persistence.release(command);
        redis.release(command, result);
        return result;
    }
}
