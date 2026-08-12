package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.port.in.ExpireReservationsUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.FindDueReservationPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistReservationExpiryPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReleaseExpiredQuotaPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationExpiryCandidate;
import com.philia.flashsale.flashsale.reservation.domain.policy.ReservationExpiryPolicy;
import java.time.Instant;
import java.util.Objects;

/** PostgreSQL decides terminal state; Redis release is deliberately outside that transaction. */
public final class ExpireReservationsService implements ExpireReservationsUseCase {
    private static final int BATCH_SIZE = 100;
    private final FindDueReservationPort dueReservations;
    private final PersistReservationExpiryPort durableExpiry;
    private final ReleaseExpiredQuotaPort quotaRelease;
    private final ReservationExpiryPolicy policy;

    public ExpireReservationsService(FindDueReservationPort dueReservations,
            PersistReservationExpiryPort durableExpiry, ReleaseExpiredQuotaPort quotaRelease,
            ReservationExpiryPolicy policy) {
        this.dueReservations = Objects.requireNonNull(dueReservations, "dueReservations");
        this.durableExpiry = Objects.requireNonNull(durableExpiry, "durableExpiry");
        this.quotaRelease = Objects.requireNonNull(quotaRelease, "quotaRelease");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public void expireDueReservations(Instant now) {
        for (ReservationExpiryCandidate candidate : dueReservations.findDue(now, BATCH_SIZE)) {
            if (policy.isDue(candidate.expiresAt(), now) && durableExpiry.persistExpiry(candidate, now)) {
                quotaRelease.release(candidate);
            }
        }
    }
}
