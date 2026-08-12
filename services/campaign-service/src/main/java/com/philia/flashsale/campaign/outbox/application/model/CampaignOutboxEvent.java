package com.philia.flashsale.campaign.outbox.application.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Application model for one durable lifecycle event awaiting a later Avro publisher. */
public record CampaignOutboxEvent(
        UUID id,
        UUID aggregateId,
        long aggregateVersion,
        String eventType,
        int eventVersion,
        String eventKey,
        String payload,
        String traceId,
        Instant occurredAt,
        Instant createdAt) {

    public CampaignOutboxEvent {
        Objects.requireNonNull(id, "Outbox event id is required");
        Objects.requireNonNull(aggregateId, "Outbox aggregate id is required");
        Objects.requireNonNull(eventType, "Outbox event type is required");
        Objects.requireNonNull(eventKey, "Outbox event key is required");
        Objects.requireNonNull(payload, "Outbox payload is required");
        Objects.requireNonNull(traceId, "Outbox trace id is required");
        Objects.requireNonNull(occurredAt, "Outbox occurrence time is required");
        Objects.requireNonNull(createdAt, "Outbox creation time is required");
    }
}
