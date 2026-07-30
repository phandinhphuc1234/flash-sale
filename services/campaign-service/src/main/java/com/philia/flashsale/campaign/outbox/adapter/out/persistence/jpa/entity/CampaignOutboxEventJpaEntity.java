package com.philia.flashsale.campaign.outbox.adapter.out.persistence.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** JPA representation of an immutable Campaign lifecycle event awaiting publication. */
@Entity(name = "CampaignOutboxEventJpaEntity")
@Table(name = "campaign_outbox_events")
public class CampaignOutboxEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_version", nullable = false)
    private long aggregateVersion;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "publish_status", nullable = false, length = 32)
    private String publishStatus;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "claimed_by", length = 100)
    private String claimedBy;

    @Column(name = "claimed_until")
    private Instant claimedUntil;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    @Column(name = "requeue_count", nullable = false)
    private int requeueCount;

    @Column(name = "requeued_by", length = 100)
    private String requeuedBy;

    @Column(name = "requeued_at")
    private Instant requeuedAt;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA; outbox workflow behavior is implemented in later tasks. */
    public CampaignOutboxEventJpaEntity() {
    }

    public CampaignOutboxEventJpaEntity(
            UUID id,
            UUID aggregateId,
            long aggregateVersion,
            String eventType,
            int eventVersion,
            String eventKey,
            String payload,
            String publishStatus,
            int retryCount,
            Instant nextAttemptAt,
            String claimedBy,
            Instant claimedUntil,
            Instant occurredAt,
            Instant publishedAt,
            String lastError,
            int requeueCount,
            String requeuedBy,
            Instant requeuedAt,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.aggregateVersion = aggregateVersion;
        this.eventType = eventType;
        this.eventVersion = eventVersion;
        this.eventKey = eventKey;
        this.payload = payload;
        this.publishStatus = publishStatus;
        this.retryCount = retryCount;
        this.nextAttemptAt = nextAttemptAt;
        this.claimedBy = claimedBy;
        this.claimedUntil = claimedUntil;
        this.occurredAt = occurredAt;
        this.publishedAt = publishedAt;
        this.lastError = lastError;
        this.requeueCount = requeueCount;
        this.requeuedBy = requeuedBy;
        this.requeuedAt = requeuedAt;
        this.traceId = traceId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    @PrePersist
    void initializeCreateTimestamps() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }
    public long getAggregateVersion() { return aggregateVersion; }
    public void setAggregateVersion(long aggregateVersion) { this.aggregateVersion = aggregateVersion; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public int getEventVersion() { return eventVersion; }
    public void setEventVersion(int eventVersion) { this.eventVersion = eventVersion; }
    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getPublishStatus() { return publishStatus; }
    public void setPublishStatus(String publishStatus) { this.publishStatus = publishStatus; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(Instant nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public String getClaimedBy() { return claimedBy; }
    public void setClaimedBy(String claimedBy) { this.claimedBy = claimedBy; }
    public Instant getClaimedUntil() { return claimedUntil; }
    public void setClaimedUntil(Instant claimedUntil) { this.claimedUntil = claimedUntil; }
    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public int getRequeueCount() { return requeueCount; }
    public void setRequeueCount(int requeueCount) { this.requeueCount = requeueCount; }
    public String getRequeuedBy() { return requeuedBy; }
    public void setRequeuedBy(String requeuedBy) { this.requeuedBy = requeuedBy; }
    public Instant getRequeuedAt() { return requeuedAt; }
    public void setRequeuedAt(Instant requeuedAt) { this.requeuedAt = requeuedAt; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
