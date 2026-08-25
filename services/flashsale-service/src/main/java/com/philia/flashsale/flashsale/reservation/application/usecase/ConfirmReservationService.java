package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.in.ConfirmReservationUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationConfirmationPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationConfirmationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult;
import java.util.Objects;

/** Coordinates durable-first confirmation and retryable Redis reconciliation. */
public final class ConfirmReservationService implements ConfirmReservationUseCase {
    private final PersistReservationConfirmationPort persistence;
    private final ReconcileReservationConfirmationPort redis;

    public ConfirmReservationService(PersistReservationConfirmationPort persistence,
            ReconcileReservationConfirmationPort redis) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.redis = Objects.requireNonNull(redis, "redis");
    }

    @Override
    public ReservationConfirmationResult confirm(ConfirmReservationCommand command) {
        ReservationConfirmationResult result = persistence.confirm(command);
        // If this call fails, the Kafka consumer deliberately does not ack. A replay
        // reads the already durable inbox/outbox row and retries only reconciliation.
        redis.confirm(command, result);
        return result;
    }
}
