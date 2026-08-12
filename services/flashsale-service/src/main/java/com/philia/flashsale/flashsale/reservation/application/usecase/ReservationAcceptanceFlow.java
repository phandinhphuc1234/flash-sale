package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.port.out.AcknowledgeReservationHandoffPort;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationAcceptanceResult;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.time.Instant;
import java.util.Objects;

/** Coordinates durable acceptance and handoff acknowledgement without owning persistence technology. */
public final class ReservationAcceptanceFlow {
    private final PersistAcceptedPurchasePort durableAcceptance;
    private final AcknowledgeReservationHandoffPort handoffAcknowledgement;

    public ReservationAcceptanceFlow(PersistAcceptedPurchasePort durableAcceptance,
            AcknowledgeReservationHandoffPort handoffAcknowledgement) {
        this.durableAcceptance = Objects.requireNonNull(durableAcceptance, "durableAcceptance");
        this.handoffAcknowledgement = Objects.requireNonNull(handoffAcknowledgement, "handoffAcknowledgement");
    }

    public ReservationAcceptanceResult accept(AcceptedReservationSnapshot snapshot, String handoffEntryId,
            Instant now) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(handoffEntryId, "handoffEntryId");
        Objects.requireNonNull(now, "now");
        if (!now.isBefore(snapshot.expiresAt())) {
            return new ReservationAcceptanceResult(ReservationAcceptanceResult.Outcome.RESERVATION_EXPIRED, null,
                    false);
        }
        try {
            durableAcceptance.persist(snapshot);
        } catch (RuntimeException exception) {
            return new ReservationAcceptanceResult(ReservationAcceptanceResult.Outcome.ACCEPTANCE_PENDING, null,
                    false);
        }
        try {
            handoffAcknowledgement.acknowledge(snapshot.reservationId(), handoffEntryId);
            return new ReservationAcceptanceResult(ReservationAcceptanceResult.Outcome.DURABLY_ACCEPTED, snapshot,
                    true);
        } catch (RuntimeException exception) {
            // The database commit is already durable; recovery can acknowledge the same entry later.
            return new ReservationAcceptanceResult(ReservationAcceptanceResult.Outcome.DURABLY_ACCEPTED, snapshot,
                    false);
        }
    }
}
