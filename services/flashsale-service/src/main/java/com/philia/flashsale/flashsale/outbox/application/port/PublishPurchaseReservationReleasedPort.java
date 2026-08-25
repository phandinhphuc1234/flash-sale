package com.philia.flashsale.flashsale.outbox.application.port;

/** Publishes one durable reservation-released outcome outside the claim transaction. */
public interface PublishPurchaseReservationReleasedPort extends PublishOutboxEventPort {
}
