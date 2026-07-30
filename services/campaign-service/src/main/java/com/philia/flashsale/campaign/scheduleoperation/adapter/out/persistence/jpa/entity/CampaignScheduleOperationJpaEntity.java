package com.philia.flashsale.campaign.scheduleoperation.adapter.out.persistence.jpa.entity;

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

/** JPA representation of a durable, indefinitely retained schedule operation. */
@Entity(name = "CampaignScheduleOperationJpaEntity")
@Table(name = "campaign_schedule_operations")
public class CampaignScheduleOperationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "inventory_request_id", nullable = false, unique = true)
    private UUID inventoryRequestId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_hash", nullable = false, columnDefinition = "char(64)")
    private String requestHash;

    @Column(name = "campaign_version", nullable = false)
    private long campaignVersion;

    @Column(name = "operation_status", nullable = false, length = 32)
    private String operationStatus;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_failure_code", length = 100)
    private String lastFailureCode;

    @Column(name = "last_failure_message", length = 1000)
    private String lastFailureMessage;

    @Column(name = "initiated_by", nullable = false, length = 100)
    private String initiatedBy;

    @Column(name = "caller_service", length = 100)
    private String callerService;

    @Column(name = "trace_id", nullable = false, length = 128)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Required by JPA; schedule-operation mapping belongs to the persistence adapter. */
    public CampaignScheduleOperationJpaEntity() {
    }

    public CampaignScheduleOperationJpaEntity(
            UUID id,
            UUID campaignId,
            String idempotencyKey,
            UUID inventoryRequestId,
            String requestHash,
            long campaignVersion,
            String operationStatus,
            int attemptCount,
            String lastFailureCode,
            String lastFailureMessage,
            String initiatedBy,
            String callerService,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.campaignId = campaignId;
        this.idempotencyKey = idempotencyKey;
        this.inventoryRequestId = inventoryRequestId;
        this.requestHash = requestHash;
        this.campaignVersion = campaignVersion;
        this.operationStatus = operationStatus;
        this.attemptCount = attemptCount;
        this.lastFailureCode = lastFailureCode;
        this.lastFailureMessage = lastFailureMessage;
        this.initiatedBy = initiatedBy;
        this.callerService = callerService;
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
    public UUID getCampaignId() { return campaignId; }
    public void setCampaignId(UUID campaignId) { this.campaignId = campaignId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public UUID getInventoryRequestId() { return inventoryRequestId; }
    public void setInventoryRequestId(UUID inventoryRequestId) { this.inventoryRequestId = inventoryRequestId; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String requestHash) { this.requestHash = requestHash; }
    public long getCampaignVersion() { return campaignVersion; }
    public void setCampaignVersion(long campaignVersion) { this.campaignVersion = campaignVersion; }
    public String getOperationStatus() { return operationStatus; }
    public void setOperationStatus(String operationStatus) { this.operationStatus = operationStatus; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public String getLastFailureCode() { return lastFailureCode; }
    public void setLastFailureCode(String lastFailureCode) { this.lastFailureCode = lastFailureCode; }
    public String getLastFailureMessage() { return lastFailureMessage; }
    public void setLastFailureMessage(String lastFailureMessage) { this.lastFailureMessage = lastFailureMessage; }
    public String getInitiatedBy() { return initiatedBy; }
    public void setInitiatedBy(String initiatedBy) { this.initiatedBy = initiatedBy; }
    public String getCallerService() { return callerService; }
    public void setCallerService(String callerService) { this.callerService = callerService; }
    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
