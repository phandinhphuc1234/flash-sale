package com.philia.flashsale.flashsale.outbox.application.port;

/** Publishes one durable reservation-confirmed outcome outside the claim transaction. */
public interface PublishPurchaseReservationConfirmedPort extends PublishOutboxEventPort {
}
