package com.philia.flashsale.campaign.scheduleoperation.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model for the durable schedule-operation workflow.
 *
 * <p>The operation owns the idempotency key, request fingerprint, and Inventory request identity
 * so retries and recovery cannot silently become a different command.</p>
 */
public final class ScheduleOperation {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_FAILURE_CODE_LENGTH = 100;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

    private final UUID id;
    private final UUID campaignId;
    private final String idempotencyKey;
    private final UUID inventoryRequestId;
    private final String requestHash;
    private final long campaignVersion;
    private ScheduleOperationStatus status;
    private int attemptCount;
    private String lastFailureCode;
    private String lastFailureMessage;
    private final String initiatedBy;
    private final String callerService;
    private final String traceId;
    private final Instant createdAt;
    private Instant updatedAt;

    private ScheduleOperation(
            UUID id,
            UUID campaignId,
            String idempotencyKey,
            UUID inventoryRequestId,
            ScheduleOperationFingerprint fingerprint,
            long campaignVersion,
            ScheduleOperationStatus status,
            int attemptCount,
            String lastFailureCode,
            String lastFailureMessage,
            String initiatedBy,
            String callerService,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "Schedule operation id is required");
        this.campaignId = Objects.requireNonNull(campaignId, "Campaign id is required");
        this.idempotencyKey = normalizeIdempotencyKey(idempotencyKey);
        this.inventoryRequestId = Objects.requireNonNull(inventoryRequestId, "Inventory request id is required");
        this.requestHash = Objects.requireNonNull(fingerprint, "Schedule operation fingerprint is required").value();
        if (campaignVersion < 0) {
            throw new IllegalArgumentException("Campaign version must not be negative");
        }
        this.campaignVersion = campaignVersion;
        this.status = Objects.requireNonNull(status, "Schedule operation status is required");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("Schedule operation attempt count must not be negative");
        }
        this.attemptCount = attemptCount;
        this.lastFailureCode = normalizeFailureCode(lastFailureCode);
        this.lastFailureMessage = normalizeFailureMessage(lastFailureMessage);
        this.initiatedBy = requireActor(initiatedBy, "Schedule operation initiator is required");
        this.callerService = normalizeOptional(callerService, 100, "Caller service");
        this.traceId = requireActor(traceId, "Schedule operation trace id is required");
        this.createdAt = Objects.requireNonNull(createdAt, "Schedule operation creation time is required");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Schedule operation update time is required");
    }

    /** Starts the first durable attempt with a stable operation and Inventory request identity. */
    public static ScheduleOperation start(
            UUID id,
            UUID campaignId,
            String idempotencyKey,
            UUID inventoryRequestId,
            ScheduleOperationFingerprint fingerprint,
            long campaignVersion,
            String initiatedBy,
            String callerService,
            String traceId,
            Instant now) {
        return new ScheduleOperation(
                id,
                campaignId,
                idempotencyKey,
                inventoryRequestId,
                fingerprint,
                campaignVersion,
                ScheduleOperationStatus.STARTED,
                1,
                null,
                null,
                initiatedBy,
                callerService,
                traceId,
                now,
                now);
    }

    /** Rehydrates the operation without applying a new transition. */
    public static ScheduleOperation rehydrate(
            UUID id,
            UUID campaignId,
            String idempotencyKey,
            UUID inventoryRequestId,
            ScheduleOperationFingerprint fingerprint,
            long campaignVersion,
            ScheduleOperationStatus status,
            int attemptCount,
            String lastFailureCode,
            String lastFailureMessage,
            String initiatedBy,
            String callerService,
            String traceId,
            Instant createdAt,
            Instant updatedAt) {
        return new ScheduleOperation(
                id,
                campaignId,
                idempotencyKey,
                inventoryRequestId,
                fingerprint,
                campaignVersion,
                status,
                attemptCount,
                lastFailureCode,
                lastFailureMessage,
                initiatedBy,
                callerService,
                traceId,
                createdAt,
                updatedAt);
    }

    /** Moves a started operation to the allocation-confirmed state. */
    public void markInventoryAllocated() {
        transitionTo(ScheduleOperationStatus.INVENTORY_ALLOCATED, updatedAt);
    }

    /** Completes an operation only after the allocation and Campaign transition have succeeded. */
    public void markCompleted() {
        transitionTo(ScheduleOperationStatus.COMPLETED, updatedAt);
    }

    /** Records a known business rejection without changing the stable operation identity. */
    public void markFailed(String failureCode, String failureMessage) {
        if (!status.canTransitionTo(ScheduleOperationStatus.FAILED)) {
            throw new IllegalStateException("Schedule operation cannot be marked failed from " + status);
        }
        lastFailureCode = normalizeFailureCode(failureCode);
        lastFailureMessage = normalizeFailureMessage(failureMessage);
        status = ScheduleOperationStatus.FAILED;
    }

    /** Reopens a retained failed operation as the same command and increments its attempt count. */
    public void retry(Instant now) {
        transitionTo(ScheduleOperationStatus.STARTED, Objects.requireNonNull(now, "Retry time is required"));
        attemptCount++;
        lastFailureCode = null;
        lastFailureMessage = null;
    }

    /** Checks whether a retry key still represents this exact retained command identity. */
    public boolean matches(String candidateIdempotencyKey, ScheduleOperationFingerprint candidateFingerprint,
                           long candidateCampaignVersion) {
        return idempotencyKey.equals(normalizeIdempotencyKey(candidateIdempotencyKey))
                && requestHash.equals(Objects.requireNonNull(candidateFingerprint, "Fingerprint is required").value())
                && campaignVersion == candidateCampaignVersion;
    }

    private void transitionTo(ScheduleOperationStatus target, Instant transitionTime) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Schedule operation cannot transition from " + status + " to " + target);
        }
        status = target;
        updatedAt = transitionTime;
    }

    private static String normalizeIdempotencyKey(String value) {
        String normalized = requireActor(value, "Schedule operation idempotency key is required");
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException("Schedule operation idempotency key must be at most 128 characters");
        }
        return normalized;
    }

    private static String normalizeFailureCode(String value) {
        return normalizeOptional(value, MAX_FAILURE_CODE_LENGTH, "Failure code");
    }

    private static String normalizeFailureMessage(String value) {
        return normalizeOptional(value, MAX_FAILURE_MESSAGE_LENGTH, "Failure message");
    }

    private static String normalizeOptional(String value, int maxLength, String fieldName) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static String requireActor(String value, String message) {
        Objects.requireNonNull(value, message);
        if (value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    public UUID id() { return id; }
    public UUID campaignId() { return campaignId; }
    public String idempotencyKey() { return idempotencyKey; }
    public UUID inventoryRequestId() { return inventoryRequestId; }
    public String requestHash() { return requestHash; }
    public long campaignVersion() { return campaignVersion; }
    public ScheduleOperationStatus status() { return status; }
    public int attemptCount() { return attemptCount; }
    public String lastFailureCode() { return lastFailureCode; }
    public String lastFailureMessage() { return lastFailureMessage; }
    public String initiatedBy() { return initiatedBy; }
    public String callerService() { return callerService; }
    public String traceId() { return traceId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
