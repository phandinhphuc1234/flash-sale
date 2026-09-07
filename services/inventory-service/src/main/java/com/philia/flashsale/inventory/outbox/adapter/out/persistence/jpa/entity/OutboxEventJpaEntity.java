package com.philia.flashsale.inventory.outbox.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEventJpaEntity {
    @Id private UUID id;
    @Column(name = "aggregate_type", nullable = false, length = 80) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false, length = 120) private String eventType;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "retry_count", nullable = false) private int retryCount;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "published_at") private Instant publishedAt;
    @Column(name = "aggregate_version", nullable = false) private long aggregateVersion;
    @Column(name = "event_version", nullable = false) private int eventVersion;
    @Column(name = "event_key", length = 64) private String eventKey;
    @Column(name = "correlation_id") private UUID correlationId;
    @Column(name = "causation_id") private UUID causationId;
    @Column(length = 256) private String traceparent;
    @Column(length = 512) private String tracestate;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "claimed_by", length = 200) private String claimedBy;
    @Column(name = "claim_until") private Instant claimUntil;

    protected OutboxEventJpaEntity() {
    }

    public OutboxEventJpaEntity(
            UUID id,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String payload,
            Instant occurredAt) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = "PENDING";
        this.retryCount = 0;
        this.occurredAt = occurredAt;
        this.aggregateVersion = 1;
        this.eventVersion = 1;
        this.nextAttemptAt = occurredAt;
    }

    public OutboxEventJpaEntity(UUID id, String aggregateType, UUID aggregateId, String eventType, String payload,
            long aggregateVersion, int eventVersion, String eventKey, UUID correlationId, UUID causationId,
            String traceparent, String tracestate, Instant occurredAt) {
        this(id, aggregateType, aggregateId, eventType, payload, occurredAt);
        this.aggregateVersion = aggregateVersion;
        this.eventVersion = eventVersion;
        this.eventKey = eventKey;
        this.correlationId = correlationId;
        this.causationId = causationId;
        this.traceparent = traceparent;
        this.tracestate = tracestate;
    }

    public void claim(String workerId, Instant until) {
        this.claimedBy = workerId;
        this.claimUntil = until;
    }

    public boolean isClaimedBy(String workerId) {
        return workerId != null && workerId.equals(claimedBy);
    }

    public void markPublished(Instant now) {
        this.status = "PUBLISHED";
        this.publishedAt = now;
        this.claimedBy = null;
        this.claimUntil = null;
    }

    public void recordFailure(Instant nextAttemptAt, boolean terminal) {
        this.retryCount++;
        this.status = terminal ? "FAILED" : "PENDING";
        this.nextAttemptAt = nextAttemptAt;
        this.claimedBy = null;
        this.claimUntil = null;
    }

    /** Replays the exact durable fact after a duplicate command without inventing a new event ID. */
    public void requeue(Instant now) {
        if (!"PUBLISHED".equals(status) && !"FAILED".equals(status)) {
            return;
        }
        this.status = "PENDING";
        this.retryCount = 0;
        this.nextAttemptAt = now;
        this.claimedBy = null;
        this.claimUntil = null;
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getOccurredAt() { return occurredAt; }
    public long getAggregateVersion() { return aggregateVersion; }
    public int getEventVersion() { return eventVersion; }
    public String getEventKey() { return eventKey; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getCausationId() { return causationId; }
    public String getTraceparent() { return traceparent; }
    public String getTracestate() { return tracestate; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getClaimedBy() { return claimedBy; }
    public Instant getClaimUntil() { return claimUntil; }
}
