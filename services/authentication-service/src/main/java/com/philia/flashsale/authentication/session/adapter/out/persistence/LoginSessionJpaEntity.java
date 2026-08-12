package com.philia.flashsale.authentication.session.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.authentication.session.domain.LoginSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "user_sessions")
/** Persistence-only mapping for user session lifecycle and bounded request metadata. */
public class LoginSessionJpaEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private LoginSessionStatus status;
    @Column(length = 150) private String deviceName;
    @Column(length = 512) private String userAgent;
    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", columnDefinition = "inet") private String ipAddress;
    @Column(nullable = false) private Instant createdAt;
    private Instant lastActivityAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant revokedAt;
    @Column(length = 100) private String revokeReason;

    protected LoginSessionJpaEntity() { }

    public LoginSessionJpaEntity(UUID id, UUID userId, LoginSessionStatus status, String deviceName,
                                 String userAgent, String ipAddress, Instant createdAt, Instant lastActivityAt,
                                 Instant expiresAt, Instant revokedAt, String revokeReason) {
        this.id = id; this.userId = userId; this.status = status; this.deviceName = deviceName;
        this.userAgent = userAgent; this.ipAddress = ipAddress; this.createdAt = createdAt;
        this.lastActivityAt = lastActivityAt; this.expiresAt = expiresAt; this.revokedAt = revokedAt;
        this.revokeReason = revokeReason;
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

    public void recordActivity(Instant value) { lastActivityAt = value; }
    public void revoke(LoginSessionStatus value, Instant at, String reason) { status = value; revokedAt = at; revokeReason = reason; }
}
