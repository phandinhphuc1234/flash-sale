package com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable safe Payment fact awaiting or recording Kafka publication. */
@Entity
@Table(name = "payment_outbox_events")
public class PaymentOutboxEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false)
    private UUID eventId;
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;
    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;
    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;
    @Column(name = "event_version", nullable = false)
    private int eventVersion;
    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;
    @Column(name = "message_key", nullable = false)
    private UUID messageKey;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(length = 255)
    private String traceparent;
    @Column(length = 255)
    private String tracestate;
    @Column(nullable = false, length = 24)
    private String status;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;
    @Column(name = "lease_owner", length = 128)
    private String leaseOwner;
    @Column(name = "lease_until")
    private Instant leaseUntil;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentOutboxEventJpaEntity() {
    }

    public static PaymentOutboxEventJpaEntity pending(UUID eventId, UUID aggregateId,
            long aggregateVersion, String eventType, int eventVersion, String topicName,
            UUID messageKey, String payload, String traceparent, String tracestate, Instant now) {
        PaymentOutboxEventJpaEntity entity = new PaymentOutboxEventJpaEntity();
        entity.eventId = eventId;
        entity.aggregateId = aggregateId;
        entity.aggregateVersion = aggregateVersion;
        entity.eventType = eventType;
        entity.eventVersion = eventVersion;
        entity.topicName = topicName;
        entity.messageKey = messageKey;
        entity.payload = payload;
        entity.traceparent = traceparent;
        entity.tracestate = tracestate;
        entity.status = "PENDING";
        entity.attemptCount = 0;
        entity.nextAttemptAt = now;
        entity.createdAt = now;
        return entity;
    }

    public void claim(String owner, Instant leaseUntil) {
        this.status = "IN_PROGRESS";
        this.leaseOwner = owner;
        this.leaseUntil = leaseUntil;
        this.attemptCount++;
    }

    public void markPublished(Instant publishedAt) {
        this.status = "PUBLISHED";
        this.publishedAt = publishedAt;
        this.leaseOwner = null;
        this.leaseUntil = null;
    }

    public UUID getEventId() { return eventId; }
    public UUID getAggregateId() { return aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public String getEventType() { return eventType; }
    public int getEventVersion() { return eventVersion; }
    public String getTopicName() { return topicName; }
    public UUID getMessageKey() { return messageKey; }
    public String getPayload() { return payload; }
    public String getTraceparent() { return traceparent; }
    public String getTracestate() { return tracestate; }
    public String getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLeaseOwner() { return leaseOwner; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
