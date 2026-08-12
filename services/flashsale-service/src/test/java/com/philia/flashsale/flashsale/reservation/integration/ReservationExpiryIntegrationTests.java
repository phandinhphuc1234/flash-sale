package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.flashsale.reservation.application.port.out.FindDueReservationPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationExpiryPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReleaseExpiredQuotaPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationExpiryCandidate;
import com.philia.flashsale.flashsale.reservation.application.usecase.ExpireReservationsService;
import com.philia.flashsale.flashsale.reservation.domain.policy.ReservationExpiryPolicy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ReservationExpiryIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T12:05:00Z");

    @Test
    void releasesOnlyAfterDurableExpiryAndRetriesWhenPostgresIsUnavailable() {
        AtomicInteger releases = new AtomicInteger();
        ReservationExpiryCandidate candidate = candidate();
        FindDueReservationPort due = (now, batch) -> List.of(candidate);
        PersistReservationExpiryPort unavailable = (item, now) -> { throw new IllegalStateException("postgres down"); };
        ReleaseExpiredQuotaPort release = item -> releases.incrementAndGet();
        ExpireReservationsService service = new ExpireReservationsService(due, unavailable, release,
                new ReservationExpiryPolicy());

        assertThatThrownBy(() -> service.expireDueReservations(NOW)).isInstanceOf(IllegalStateException.class);
        assertThat(releases).hasValue(0);

        new ExpireReservationsService(due, (item, now) -> true, release, new ReservationExpiryPolicy())
                .expireDueReservations(NOW);
        assertThat(releases).hasValue(1);
    }

    @Test
    void ignoresAReservationBeforeItsFiveMinuteEligibilityBoundary() {
        AtomicInteger persisted = new AtomicInteger();
        new ExpireReservationsService((now, batch) -> List.of(candidate()), (item, now) -> {
            persisted.incrementAndGet(); return true;
        }, item -> { }, new ReservationExpiryPolicy()).expireDueReservations(NOW.minusSeconds(1));
        assertThat(persisted).hasValue(0);
    }

    private ReservationExpiryCandidate candidate() {
        return new ReservationExpiryCandidate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), 2, NOW);
    }
}
