package com.philia.flashsale.campaign.campaign.domain.model;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** Aggregate root protecting Campaign identity, draft mutability, and lifecycle monotonicity. */
public final class Campaign {

    private static final int MAX_CODE_LENGTH = 64;
    private static final int MAX_NAME_LENGTH = 200;

    private final UUID id;
    private final String code;
    private String name;
    private Instant startAt;
    private Instant endAt;
    private CampaignStatus status;
    private Instant scheduledAt;
    private Instant activatedAt;
    private Instant endedAt;
    private long version;
    private final String createdBy;
    private String updatedBy;
    private final Instant createdAt;
    private Instant updatedAt;
    private CampaignItem item;

    private Campaign(
            UUID id,
            String code,
            String name,
            Instant startAt,
            Instant endAt,
            CampaignStatus status,
            Instant scheduledAt,
            Instant activatedAt,
            Instant endedAt,
            long version,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt,
            CampaignItem item) {
        this.id = id == null ? UUID.randomUUID() : id;
        this.code = normalizeCode(code);
        this.name = normalizeName(name);
        this.startAt = requireInstant(startAt, "Campaign start time is required");
        this.endAt = requireInstant(endAt, "Campaign end time is required");
        requireValidWindow(this.startAt, this.endAt);
        this.status = status == null ? CampaignStatus.DRAFT : status;
        this.scheduledAt = scheduledAt;
        this.activatedAt = activatedAt;
        this.endedAt = endedAt;
        this.version = requireVersion(version);
        this.createdBy = requireActor(createdBy, "Campaign creator is required");
        this.updatedBy = requireActor(updatedBy, "Campaign updater is required");
        this.createdAt = requireInstant(createdAt, "Campaign creation time is required");
        this.updatedAt = requireInstant(updatedAt, "Campaign update time is required");
        this.item = item;
    }

    /** Creates a new draft with version zero and no configured item. */
    public static Campaign createDraft(
            UUID id,
            String code,
            String name,
            Instant startAt,
            Instant endAt,
            String actor,
            Instant now) {
        return new Campaign(
                id,
                code,
                name,
                startAt,
                endAt,
                CampaignStatus.DRAFT,
                null,
                null,
                null,
                0,
                actor,
                actor,
                now,
                now,
                null);
    }

    /** Rehydrates an aggregate from persistence without applying a new lifecycle operation. */
    public static Campaign rehydrate(
            UUID id,
            String code,
            String name,
            Instant startAt,
            Instant endAt,
            CampaignStatus status,
            Instant scheduledAt,
            Instant activatedAt,
            Instant endedAt,
            long version,
            String createdBy,
            String updatedBy,
            Instant createdAt,
            Instant updatedAt,
            CampaignItem item) {
        return new Campaign(
                id,
                code,
                name,
                startAt,
                endAt,
                status,
                scheduledAt,
                activatedAt,
                endedAt,
                version,
                createdBy,
                updatedBy,
                createdAt,
                updatedAt,
                item);
    }

    /** Replaces draft metadata and advances the aggregate version once. */
    public void replaceMetadata(String name, Instant startAt, Instant endAt, String actor, Instant now) {
        ensureDraftEditable();
        String normalizedName = normalizeName(name);
        Instant normalizedStart = requireInstant(startAt, "Campaign start time is required");
        Instant normalizedEnd = requireInstant(endAt, "Campaign end time is required");
        String normalizedActor = requireActor(actor, "Campaign updater is required");
        Instant normalizedNow = requireInstant(now, "Campaign update time is required");
        requireValidWindow(normalizedStart, normalizedEnd);
        this.name = normalizedName;
        this.startAt = normalizedStart;
        this.endAt = normalizedEnd;
        touch(normalizedActor, normalizedNow);
    }

    /** Replaces the one draft item and advances the aggregate version once. */
    public void replaceItem(CampaignItem item, String actor, Instant now) {
        ensureDraftEditable();
        if (item == null) {
            throw new IllegalArgumentException("Campaign item is required");
        }
        String normalizedActor = requireActor(actor, "Campaign updater is required");
        Instant normalizedNow = requireInstant(now, "Campaign update time is required");
        this.item = item;
        touch(normalizedActor, normalizedNow);
    }

    /** Finalizes a draft only after its complete snapshot/allocation and future start are present. */
    public void markScheduled(String actor, Instant now) {
        requireTransition(CampaignStatus.SCHEDULED);
        Instant transitionTime = requireInstant(now, "Schedule time is required");
        String normalizedActor = requireActor(actor, "Campaign updater is required");
        if (!transitionTime.isBefore(startAt)) {
            throw new IllegalStateException("Campaign cannot be scheduled after its start time");
        }
        if (item == null || !item.isReadyForScheduling()) {
            throw new IllegalStateException("Campaign item snapshot and allocation are incomplete");
        }
        status = CampaignStatus.SCHEDULED;
        scheduledAt = transitionTime;
        touch(normalizedActor, transitionTime);
    }

    /** Activates a scheduled Campaign only inside its configured sale window. */
    public void markActive(String actor, Instant now) {
        requireTransition(CampaignStatus.ACTIVE);
        Instant transitionTime = requireInstant(now, "Activation time is required");
        String normalizedActor = requireActor(actor, "Campaign updater is required");
        if (transitionTime.isBefore(startAt) || !transitionTime.isBefore(endAt)) {
            throw new IllegalStateException("Campaign activation must be inside its sale window");
        }
        if (item == null || !item.isReadyForScheduling()) {
            throw new IllegalStateException("Campaign item snapshot and allocation are incomplete");
        }
        status = CampaignStatus.ACTIVE;
        activatedAt = transitionTime;
        touch(normalizedActor, transitionTime);
    }

    /** Ends an active Campaign at or after its configured end boundary. */
    public void markEnded(String actor, Instant now) {
        requireTransition(CampaignStatus.ENDED);
        Instant transitionTime = requireInstant(now, "End time is required");
        String normalizedActor = requireActor(actor, "Campaign updater is required");
        if (transitionTime.isBefore(endAt)) {
            throw new IllegalStateException("Campaign cannot end before its end time");
        }
        status = CampaignStatus.ENDED;
        endedAt = transitionTime;
        touch(normalizedActor, transitionTime);
    }

    public boolean isEditable() {
        return status.isEditable();
    }

    public boolean hasItem() {
        return item != null;
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public Instant startAt() {
        return startAt;
    }

    public Instant endAt() {
        return endAt;
    }

    public CampaignStatus status() {
        return status;
    }

    public Instant scheduledAt() {
        return scheduledAt;
    }

    public Instant activatedAt() {
        return activatedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public long version() {
        return version;
    }

    public String createdBy() {
        return createdBy;
    }

    public String updatedBy() {
        return updatedBy;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public CampaignItem item() {
        return item;
    }

    private void ensureDraftEditable() {
        if (!status.isEditable()) {
            throw new IllegalStateException("Only a draft Campaign can be edited");
        }
    }

    private void requireTransition(CampaignStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid Campaign lifecycle transition: " + status + " -> " + target);
        }
    }

    private void touch(String actor, Instant now) {
        updatedBy = requireActor(actor, "Campaign updater is required");
        updatedAt = requireInstant(now, "Campaign update time is required");
        version = Math.addExact(version, 1);
    }

    private static String normalizeCode(String value) {
        String normalized = requireText(value, "Campaign code is required").toUpperCase(Locale.ROOT);
        if (normalized.length() > MAX_CODE_LENGTH) {
            throw new IllegalArgumentException("Campaign code cannot exceed 64 characters");
        }
        return normalized;
    }

    private static String normalizeName(String value) {
        String normalized = requireText(value, "Campaign name is required");
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Campaign name cannot exceed 200 characters");
        }
        return normalized;
    }

    private static void requireValidWindow(Instant startAt, Instant endAt) {
        if (!startAt.isBefore(endAt)) {
            throw new IllegalArgumentException("Campaign start time must be before end time");
        }
    }

    private static String requireActor(String value, String message) {
        return requireText(value, message);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static Instant requireInstant(Instant value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static long requireVersion(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Campaign version cannot be negative");
        }
        return value;
    }
}
