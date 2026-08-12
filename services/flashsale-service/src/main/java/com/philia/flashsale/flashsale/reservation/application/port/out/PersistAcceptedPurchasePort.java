package com.philia.flashsale.flashsale.reservation.application.port.out;

import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;

/** Future durable capability that commits purchase, reservation, idempotency, and outbox state together. */
public interface PersistAcceptedPurchasePort {
    void persist(AcceptedReservationSnapshot snapshot);
}
