package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.application.command.ReserveCampaignQuotaCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.time.Instant;
import java.util.UUID;

/** Output capability for the one Redis Lua admission decision and recovery handoff. */
public interface ExecuteAtomicReservationPort {
    ReservationDecisionResult execute(AtomicReservationRequest request);

    record AtomicReservationRequest(
            ReserveCampaignQuotaCommand command,
            String idempotencyKeyHash,
            String requestHash,
            UUID purchaseRequestId,
            UUID reservationId,
            UUID eventId,
            Instant acceptedAt,
            Instant expiresAt,
            Instant retainedUntil) {
        public AtomicReservationRequest {
            if (command == null || idempotencyKeyHash == null || requestHash == null
                    || purchaseRequestId == null || reservationId == null || eventId == null
                    || acceptedAt == null || expiresAt == null || retainedUntil == null) {
                throw new NullPointerException("atomic reservation request fields must not be null");
            }
        }
    }
}
