package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.command.ReserveCampaignQuotaCommand;
import com.philia.flashsale.flashsale.reservation.application.port.in.ReserveCampaignQuotaUseCase;
import com.philia.flashsale.flashsale.reservation.application.port.out.ExecuteAtomicReservationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Prepares stable hashes/identities and delegates the business admission decision to atomic Redis Lua. */
public final class ReserveCampaignQuotaService implements ReserveCampaignQuotaUseCase {
    private final ExecuteAtomicReservationPort atomicReservation;
    private final ReservationFingerprintService fingerprints;
    private final ReservationIdentityFactory identities;
    private final Clock clock;
    private final Duration reservationTtl;
    private final Duration idempotencyRetention;

    public ReserveCampaignQuotaService(ExecuteAtomicReservationPort atomicReservation,
            ReservationFingerprintService fingerprints, ReservationIdentityFactory identities, Clock clock,
            Duration reservationTtl, Duration idempotencyRetention) {
        this.atomicReservation = Objects.requireNonNull(atomicReservation, "atomicReservation");
        this.fingerprints = Objects.requireNonNull(fingerprints, "fingerprints");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.reservationTtl = requirePositive(reservationTtl, "reservationTtl");
        this.idempotencyRetention = requirePositive(idempotencyRetention, "idempotencyRetention");
    }

    @Override
    public ReservationDecisionResult reserve(ReserveCampaignQuotaCommand command) {
        Objects.requireNonNull(command, "command");
        Instant acceptedAt = clock.instant();
        if (!acceptedAt.isBefore(command.campaignEndsAt())) {
            throw new IllegalArgumentException("campaignEndsAt must be after the admission instant");
        }
        Instant expiresAt = acceptedAt.plus(reservationTtl);
        Instant retainedUntil = command.campaignEndsAt().plus(idempotencyRetention);
        var identity = identities.create();
        String keyHash = fingerprints.hashIdempotencyKey(command.idempotencyKey());
        String requestHash = fingerprints.hashRequest(command.userId(), command.campaignId(), command.variantId(),
                command.quantity());
        var request = new ExecuteAtomicReservationPort.AtomicReservationRequest(command, keyHash, requestHash,
                identity.purchaseRequestId(), identity.reservationId(), identity.eventId(), acceptedAt, expiresAt,
                retainedUntil);
        return atomicReservation.execute(request);
    }

    private static Duration requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
