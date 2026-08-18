package com.philia.flashsale.payment.payment.application.model.webhook;

import java.util.UUID;

/** Durable webhook acknowledgement outcome; all valid outcomes map to an empty HTTP 204. */
public record ProviderEventAcceptanceResult(Status status, UUID receiptId) {
    public enum Status { ACCEPTED, DUPLICATE, IGNORED }
}
