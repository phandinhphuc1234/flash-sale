package com.philia.flashsale.inventory.regularhold.domain.model;

import com.philia.flashsale.inventory.regularhold.domain.exception.RegularStockHoldDomainException;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Aggregate that records one all-or-nothing regular reservation and its immutable identity. */
public final class RegularStockHold {
    private final UUID id;
    private final UUID purchaseRequestId;
    private final UUID orderId;
    private final UUID shopperId;
    private final String requestFingerprint;
    private RegularStockHoldStatus status;
    private final Instant expiresAt;
    private Instant confirmedAt;
    private Instant releasedAt;
    private Instant expiredAt;
    private final Instant createdAt;
    private Instant updatedAt;
    private long version;
    private final List<RegularStockHoldItem> items;

    public RegularStockHold(UUID id, UUID purchaseRequestId, UUID orderId, UUID shopperId,
            String requestFingerprint, RegularStockHoldStatus status, Instant expiresAt,
            Instant confirmedAt, Instant releasedAt, Instant expiredAt, Instant createdAt,
            Instant updatedAt, long version, List<RegularStockHoldItem> items) {
        this.id = Objects.requireNonNull(id, "id");
        this.purchaseRequestId = Objects.requireNonNull(purchaseRequestId, "purchaseRequestId");
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.shopperId = Objects.requireNonNull(shopperId, "shopperId");
        if (requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")) {
            throw new RegularStockHoldDomainException("Regular hold fingerprint is invalid");
        }
        this.requestFingerprint = requestFingerprint;
        this.status = Objects.requireNonNull(status, "status");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        this.confirmedAt = confirmedAt;
        this.releasedAt = releasedAt;
        this.expiredAt = expiredAt;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!expiresAt.isAfter(createdAt) || version < 0) {
            throw new RegularStockHoldDomainException("Regular hold state is invalid");
        }
        boolean terminalTimestampsMatch = switch (status) {
            case HELD -> confirmedAt == null && releasedAt == null && expiredAt == null;
            case CONFIRMED -> confirmedAt != null && releasedAt == null && expiredAt == null;
            case RELEASED -> confirmedAt == null && releasedAt != null && expiredAt == null;
            case EXPIRED -> confirmedAt == null && releasedAt == null && expiredAt != null;
        };
        if (!terminalTimestampsMatch) {
            throw new RegularStockHoldDomainException("Regular hold terminal timestamps are invalid");
        }
        if (items == null || items.isEmpty() || items.size() > 20) {
            throw new RegularStockHoldDomainException("Regular hold must contain between one and twenty items");
        }
        List<RegularStockHoldItem> ordered = items.stream()
                .sorted(Comparator.comparing(RegularStockHoldItem::variantId))
                .toList();
        if (ordered.stream().map(RegularStockHoldItem::variantId).distinct().count() != ordered.size()) {
            throw new RegularStockHoldDomainException("Regular hold variants must be canonical and distinct");
        }
        this.items = ordered;
        this.version = version;
    }

    public static RegularStockHold held(UUID id, UUID purchaseRequestId, UUID orderId, UUID shopperId,
            String requestFingerprint, List<RegularStockHoldItem> items, Instant now, Duration ttl) {
        if (!Duration.ofMinutes(5).equals(ttl)) {
            throw new RegularStockHoldDomainException("Regular hold TTL must be exactly five minutes");
        }
        return new RegularStockHold(id, purchaseRequestId, orderId, shopperId, requestFingerprint,
                RegularStockHoldStatus.HELD, now.plus(ttl), null, null, null, now, now, 0, items);
    }

    public boolean hasEquivalentIdentity(UUID holdId, UUID orderId, UUID shopperId, String fingerprint) {
        return id.equals(holdId) && this.orderId.equals(orderId) && this.shopperId.equals(shopperId)
                && requestFingerprint.equals(fingerprint);
    }

    /**
     * Confirms a still-live hold exactly once. Physical stock is changed by the application service
     * in the same transaction, after it has locked the corresponding Inventory rows.
     */
    public void confirm(Instant now) {
        requireHeldBeforeExpiry(now, "confirm");
        status = RegularStockHoldStatus.CONFIRMED;
        confirmedAt = now;
        touch(now);
    }

    /** Releases a still-live hold without changing physical on-hand stock. */
    public void release(Instant now) {
        requireHeldBeforeExpiry(now, "release");
        status = RegularStockHoldStatus.RELEASED;
        releasedAt = now;
        touch(now);
    }

    /** Transitions only a still-held, due reservation to its durable expiry state. */
    public void expire(Instant now) {
        if (status != RegularStockHoldStatus.HELD || now.isBefore(expiresAt)) {
            throw new RegularStockHoldDomainException("Regular hold is not eligible for expiry");
        }
        status = RegularStockHoldStatus.EXPIRED;
        expiredAt = now;
        touch(now);
    }

    public boolean isHeld() {
        return status == RegularStockHoldStatus.HELD;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    private void requireHeldBeforeExpiry(Instant now, String transition) {
        if (status != RegularStockHoldStatus.HELD) {
            throw new RegularStockHoldDomainException("Regular hold cannot " + transition + " from " + status);
        }
        if (!now.isBefore(expiresAt)) {
            throw new RegularStockHoldDomainException("Regular hold cannot " + transition + " after expiry");
        }
    }

    private void touch(Instant now) {
        updatedAt = now;
        version++;
    }

    public UUID id() { return id; }
    public UUID purchaseRequestId() { return purchaseRequestId; }
    public UUID orderId() { return orderId; }
    public UUID shopperId() { return shopperId; }
    public String requestFingerprint() { return requestFingerprint; }
    public RegularStockHoldStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public Instant confirmedAt() { return confirmedAt; }
    public Instant releasedAt() { return releasedAt; }
    public Instant expiredAt() { return expiredAt; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
    public List<RegularStockHoldItem> items() { return items; }
}
