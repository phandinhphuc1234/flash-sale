package com.philia.flashsale.authentication.session.adapter.out.persistence;

import com.philia.flashsale.authentication.session.domain.LoginSession;
import com.philia.flashsale.authentication.session.domain.RefreshCredential;

/** Boundary mapper for session persistence representations. */
public final class SessionPersistenceMapper {
    private SessionPersistenceMapper() { }

    public static LoginSession toDomain(LoginSessionJpaEntity entity) {
        return LoginSession.restore(entity.id(), entity.userId(), entity.status(), entity.deviceName(),
                entity.userAgent(), entity.ipAddress(), entity.createdAt(), entity.lastActivityAt(),
                entity.expiresAt(), entity.revokedAt(), entity.revokeReason());
    }

    public static LoginSessionJpaEntity toEntity(LoginSession session) {
        return new LoginSessionJpaEntity(session.id(), session.userId(), session.status(), session.deviceName(),
                session.userAgent(), session.ipAddress(), session.createdAt(), session.lastActivityAt(),
                session.expiresAt(), session.revokedAt(), session.revokeReason());
    }

    public static RefreshCredential toDomain(RefreshCredentialJpaEntity entity) {
        return new RefreshCredential(entity.id(), entity.sessionId(), entity.tokenHash(), entity.parentTokenId(),
                entity.replacedByTokenId(), entity.issuedAt(), entity.expiresAt(), entity.usedAt(),
                entity.revokedAt(), entity.revokeReason());
    }
}
