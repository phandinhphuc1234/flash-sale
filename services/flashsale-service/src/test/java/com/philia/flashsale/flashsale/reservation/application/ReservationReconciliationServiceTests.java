package com.philia.flashsale.flashsale.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.reservation.application.port.out.LoadReservationReconciliationPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationProjectionPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReconciliationCandidate;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReservationReconciliationServiceTests {
    @Test
    void marksOnlySuccessfullyReplayedRowsAndCanResumeAfterFailure() {
        var candidate = new ReservationReconciliationCandidate(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 1, ReservationReconciliationCandidate.Status.RELEASED);
        var pending = new ArrayList<>(List.of(candidate));
        LoadReservationReconciliationPort backlog = new LoadReservationReconciliationPort() {
            @Override public List<ReservationReconciliationCandidate> findPending(int batchSize) { return List.copyOf(pending); }
            @Override public void markReconciled(UUID id, Instant at) { pending.removeIf(row -> row.reservationId().equals(id)); }
        };
        var attempts = new int[1];
        ReconcileReservationProjectionPort projection = row -> {
            if (++attempts[0] == 1) throw new IllegalStateException("redis unavailable");
        };
        var service = new ReservationReconciliationService(backlog, projection,
                Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC));

        assertThat(service.reconcileBatch(10)).isZero();
        assertThat(pending).hasSize(1);
        assertThat(service.reconcileBatch(10)).isEqualTo(1);
        assertThat(pending).isEmpty();
    }
}
