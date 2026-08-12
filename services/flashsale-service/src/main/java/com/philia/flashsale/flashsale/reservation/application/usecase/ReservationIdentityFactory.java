package com.philia.flashsale.flashsale.reservation.application.usecase;

import java.util.UUID;

/** Creates all business identities before Lua so a winner never needs a replacement identity. */
public final class ReservationIdentityFactory {
    public ReservationIdentity create() {
        return new ReservationIdentity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    public record ReservationIdentity(UUID purchaseRequestId, UUID reservationId, UUID eventId) {
        public ReservationIdentity {
            if (purchaseRequestId == null || reservationId == null || eventId == null) {
                throw new NullPointerException("reservation identities must not be null");
            }
        }
    }
}
