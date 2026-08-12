package com.philia.flashsale.authentication.session.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable domain view of one hashed refresh credential in a rotation chain. */
public record RefreshCredential(UUID id, UUID sessionId, String tokenHash, UUID parentTokenId,
                                UUID replacedByTokenId, Instant issuedAt, Instant expiresAt,
                                Instant usedAt, Instant revokedAt, String revokeReason) {

    public boolean isCurrent(Instant now) {
        return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isReplayed() {
        return usedAt != null || replacedByTokenId != null;
    }
}
