package com.philia.flashsale.payment.outbox.application.model;

/** Bounded result of one sequential outbox relay scan. */
public record PaymentOutboxPublicationResult(int claimed, int published, int retried, int leaseLost) {
}
