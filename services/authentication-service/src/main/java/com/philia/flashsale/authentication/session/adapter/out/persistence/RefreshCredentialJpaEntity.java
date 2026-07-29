package com.philia.flashsale.authentication.session.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "refresh_tokens")
/** Persistence-only mapping for one hashed refresh-token-chain row. */
public class RefreshCredentialJpaEntity {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "parent_token_id") private UUID parentTokenId;
    @Column(name = "replaced_by_token_id") private UUID replacedByTokenId;
    @Column(nullable = false) private Instant issuedAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant usedAt;
    private Instant revokedAt;
    @Column(length = 100) private String revokeReason;

    protected RefreshCredentialJpaEntity() { }

    public RefreshCredentialJpaEntity(UUID id, UUID sessionId, String tokenHash, UUID parentTokenId,
                                      UUID replacedByTokenId, Instant issuedAt, Instant expiresAt,
                                      Instant usedAt, Instant revokedAt, String revokeReason) {
        this.id = id; this.sessionId = sessionId; this.tokenHash = tokenHash; this.parentTokenId = parentTokenId;
        this.replacedByTokenId = replacedByTokenId; this.issuedAt = issuedAt; this.expiresAt = expiresAt;
        this.usedAt = usedAt; this.revokedAt = revokedAt; this.revokeReason = revokeReason;
    }

    public UUID id() { return id; }
    public UUID sessionId() { return sessionId; }
    public String tokenHash() { return tokenHash; }
    public UUID parentTokenId() { return parentTokenId; }
    public UUID replacedByTokenId() { return replacedByTokenId; }
    public Instant issuedAt() { return issuedAt; }
    public Instant expiresAt() { return expiresAt; }
    public Instant usedAt() { return usedAt; }
    public Instant revokedAt() { return revokedAt; }
    public String revokeReason() { return revokeReason; }
    public void markUsed(Instant at) { usedAt = at; }
    public void linkSuccessor(UUID successorId) { replacedByTokenId = successorId; }
    public void revoke(Instant at, String reason) { revokedAt = at; revokeReason = reason; }
}
