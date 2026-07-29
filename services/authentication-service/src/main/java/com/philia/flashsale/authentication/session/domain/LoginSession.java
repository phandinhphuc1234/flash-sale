package com.philia.flashsale.authentication.session.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Session aggregate that owns refresh rotation and revocation lifecycle. */
public final class LoginSession {
    private final UUID id;
    private final UUID userId;
    private LoginSessionStatus status;
    private final String deviceName;
    private final String userAgent;
    private final String ipAddress;
    private final Instant createdAt;
    private Instant lastActivityAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private String revokeReason;

    private LoginSession(UUID id, UUID userId, LoginSessionStatus status, String deviceName, String userAgent,
                         String ipAddress, Instant createdAt, Instant lastActivityAt, Instant expiresAt,
                         Instant revokedAt, String revokeReason) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.status = Objects.requireNonNull(status);
        this.deviceName = deviceName;
        this.userAgent = userAgent;
        this.ipAddress = ipAddress;
        this.createdAt = Objects.requireNonNull(createdAt);
        this.lastActivityAt = lastActivityAt;
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
        if (!expiresAt.isAfter(createdAt)) throw new SessionFailure("AUTH_SESSION_INVALID", "Session expiry must be after creation");
    }

    public static LoginSession create(UUID userId, String deviceName, String userAgent, String ipAddress,
                                      Instant createdAt, Instant expiresAt) {
        return new LoginSession(UUID.randomUUID(), userId, LoginSessionStatus.ACTIVE, deviceName, userAgent,
                ipAddress, createdAt, createdAt, expiresAt, null, null);
    }

    public static LoginSession restore(UUID id, UUID userId, LoginSessionStatus status, String deviceName,
                                       String userAgent, String ipAddress, Instant createdAt,
                                       Instant lastActivityAt, Instant expiresAt, Instant revokedAt,
                                       String revokeReason) {
        return new LoginSession(id, userId, status, deviceName, userAgent, ipAddress, createdAt,
                lastActivityAt, expiresAt, revokedAt, revokeReason);
    }

    public boolean activeAt(Instant now) { return status == LoginSessionStatus.ACTIVE && expiresAt.isAfter(now); }

    public void recordActivity(Instant now) {
        if (!activeAt(now)) throw new SessionFailure("AUTH_REFRESH_TOKEN_INVALID", "Session is not active");
        lastActivityAt = now;
    }

    public void revoke(String reason, Instant now) {
        status = LoginSessionStatus.REVOKED;
        revokedAt = now;
        revokeReason = reason;
    }

    public void compromise(String reason, Instant now) {
        status = LoginSessionStatus.COMPROMISED;
        revokedAt = now;
        revokeReason = reason;
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public LoginSessionStatus status() { return status; }
    public String deviceName() { return deviceName; }
    public String userAgent() { return userAgent; }
    public String ipAddress() { return ipAddress; }
    public Instant createdAt() { return createdAt; }
    public Instant lastActivityAt() { return lastActivityAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public String revokeReason() { return revokeReason; }
}
