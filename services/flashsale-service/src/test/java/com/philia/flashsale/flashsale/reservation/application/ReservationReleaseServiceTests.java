package com.philia.flashsale.flashsale.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationReleasePort;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationReleasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReleaseReservationService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ReservationReleaseServiceTests {
    @Test
    void persistsBeforeReconcilingRedisAndKeepsReleasedResultIdentity() {
        AtomicBoolean persisted = new AtomicBoolean();
        var result = result(ReservationReleaseResult.Status.RELEASED);
        PersistReservationReleasePort persistence = command -> { persisted.set(true); return result; };
        ReconcileReservationReleasePort redis = (command, actual) -> assertThat(persisted).isTrue();

        var actual = new ReleaseReservationService(persistence, redis).release(command());

        assertThat(actual).isSameAs(result);
    }

    @Test
    void doesNotTouchRedisWhenDurableReleaseFails() {
        AtomicBoolean reconciled = new AtomicBoolean();
        PersistReservationReleasePort persistence = command -> { throw new IllegalStateException("db down"); };
        ReconcileReservationReleasePort redis = (command, result) -> reconciled.set(true);

        assertThatThrownBy(() -> new ReleaseReservationService(persistence, redis).release(command()))
                .isInstanceOf(IllegalStateException.class).hasMessage("db down");
        assertThat(reconciled).isFalse();
    }

    private ReleaseReservationCommand command() {
        UUID sagaId = UUID.randomUUID();
        return new ReleaseReservationCommand(UUID.randomUUID(), "ReleasePurchaseReservation", 1,
                "order-service", "PURCHASE_SAGA", sagaId, 1, sagaId, UUID.randomUUID(),
                Instant.parse("2030-01-01T10:00:00Z"), UUID.randomUUID(), sagaId, UUID.randomUUID(),
                "PROVIDER_TERMINAL_FAILURE", "flashsale.purchase.commands.v1", 0, 1,
                "00-trace", "", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }

    private ReservationReleaseResult result(ReservationReleaseResult.Status status) {
        return new ReservationReleaseResult(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 1, UUID.randomUUID(), UUID.randomUUID(), status,
                "PROVIDER_TERMINAL_FAILURE", Instant.parse("2030-01-01T10:00:01Z"));
    }
}
