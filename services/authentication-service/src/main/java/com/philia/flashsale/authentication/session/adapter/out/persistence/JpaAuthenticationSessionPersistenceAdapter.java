package com.philia.flashsale.authentication.session.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.authentication.account.adapter.out.persistence.AccountJpaEntity;
import com.philia.flashsale.authentication.account.adapter.out.persistence.AccountJpaRepository;
import com.philia.flashsale.authentication.account.adapter.out.persistence.AccountPersistenceMapper;
import com.philia.flashsale.authentication.session.application.login.LoadAccountForAuthenticationPort;
import com.philia.flashsale.authentication.session.application.login.PersistSuccessfulLoginPort;
import com.philia.flashsale.authentication.session.application.refresh.RotateRefreshCredentialPort;
import com.philia.flashsale.authentication.session.domain.SessionFailure;
import com.philia.flashsale.authentication.session.application.logout.RevokeAuthenticationSessionsPort;
import com.philia.flashsale.authentication.cleanup.application.PurgeExpiredAuthenticationStatePort;
import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.session.domain.LoginSessionStatus;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true", matchIfMissing = true)
/** Implements account loading, atomic login persistence, locked rotation, logout, and cleanup. */
public class JpaAuthenticationSessionPersistenceAdapter implements LoadAccountForAuthenticationPort, PersistSuccessfulLoginPort,
        RotateRefreshCredentialPort, RevokeAuthenticationSessionsPort, PurgeExpiredAuthenticationStatePort {
    private final AccountJpaRepository accountRepository;
    private final LoginSessionJpaRepository sessionRepository;
    private final RefreshCredentialJpaRepository refreshRepository;

    public JpaAuthenticationSessionPersistenceAdapter(AccountJpaRepository accountRepository,
                                                      LoginSessionJpaRepository sessionRepository,
                                                      RefreshCredentialJpaRepository refreshRepository) {
        this.accountRepository = accountRepository; this.sessionRepository = sessionRepository; this.refreshRepository = refreshRepository;
    }

    @Override
    public Optional<Account> load(String normalizedLogin) {
        Optional<AccountJpaEntity> byEmail = accountRepository.findByEmailNormalized(normalizedLogin);
        return byEmail.or(() -> accountRepository.findByUsernameNormalized(normalizedLogin))
                .map(AccountPersistenceMapper::toDomain);
    }

    @Override
    @Transactional
    public UUID persist(Account account, String refreshHash, String deviceName, String userAgent, String directIp, Instant now) {
        AccountJpaEntity entity = accountRepository.findById(account.id()).orElseThrow();
        entity.updateLogin(now, now);
        accountRepository.save(entity);
        UUID sessionId = UUID.randomUUID();
        Instant sessionExpiry = now.plus(30, ChronoUnit.DAYS);
        sessionRepository.save(new LoginSessionJpaEntity(sessionId, account.id(), LoginSessionStatus.ACTIVE,
                deviceName, userAgent, directIp, now, now, sessionExpiry, null, null));
        refreshRepository.save(new RefreshCredentialJpaEntity(UUID.randomUUID(), sessionId, refreshHash, null, null,
                now, now.plus(7, ChronoUnit.DAYS), null, null, null));
        return sessionId;
    }

    @Override
    @Transactional
    public RotationOutcome rotate(String currentHash, String successorHash, Instant now) {
        RefreshCredentialJpaEntity current = refreshRepository.findByTokenHash(currentHash)
                .orElseThrow(() -> new SessionFailure("AUTH_REFRESH_TOKEN_INVALID", "Refresh credential is invalid"));
        LoginSessionJpaEntity session = sessionRepository.findById(current.sessionId())
                .orElseThrow(() -> new SessionFailure("AUTH_REFRESH_TOKEN_INVALID", "Session is invalid"));
        if (current.usedAt() != null || current.replacedByTokenId() != null) {
            compromise(session, now);
            return new RefreshReuseDetected();
        }
        if (current.revokedAt() != null || !current.expiresAt().isAfter(now)
                || session.status() != LoginSessionStatus.ACTIVE || !session.expiresAt().isAfter(now)) {
            throw new SessionFailure("AUTH_REFRESH_TOKEN_INVALID", "Refresh credential is invalid");
        }
        Account account = accountRepository.findById(session.userId()).map(AccountPersistenceMapper::toDomain)
                .orElseThrow(() -> new SessionFailure("AUTH_REFRESH_TOKEN_INVALID", "Account is invalid"));
        UUID successorId = UUID.randomUUID();
        Instant successorExpiry = Instant.ofEpochMilli(Math.min(
                now.plus(7, ChronoUnit.DAYS).toEpochMilli(), session.expiresAt().toEpochMilli()));
        // Mark the old row used first so the partial unique index releases the
        // session slot; link the successor only after its row exists.
        current.markUsed(now);
        refreshRepository.saveAndFlush(current);
        refreshRepository.save(new RefreshCredentialJpaEntity(successorId, session.id(), successorHash, current.id(), null,
                now, successorExpiry, null, null, null));
        current.linkSuccessor(successorId);
        refreshRepository.saveAndFlush(current);
        session.recordActivity(now);
        sessionRepository.save(session);
        return new RefreshRotation(account, session.id(), successorExpiry);
    }

    private void compromise(LoginSessionJpaEntity session, Instant now) {
        session.revoke(LoginSessionStatus.COMPROMISED, now, "REFRESH_TOKEN_REUSE_DETECTED");
        sessionRepository.save(session);
        refreshRepository.findAllBySessionId(session.id()).stream()
                .filter(token -> token.revokedAt() == null)
                .forEach(token -> { token.revoke(now, "REFRESH_TOKEN_REUSE_DETECTED"); refreshRepository.save(token); });
    }

    @Override
    @Transactional
    public void revokeCurrent(String refreshHash, Instant now) {
        refreshRepository.findByTokenHash(refreshHash).ifPresent(token -> {
            LoginSessionJpaEntity session = sessionRepository.findById(token.sessionId()).orElse(null);
            if (session != null && session.status() == LoginSessionStatus.ACTIVE) {
                session.revoke(LoginSessionStatus.REVOKED, now, "LOGOUT_CURRENT");
                sessionRepository.save(session);
                refreshRepository.findAllBySessionId(session.id()).forEach(value -> { value.revoke(now, "LOGOUT_CURRENT"); refreshRepository.save(value); });
            }
        });
    }

    @Override
    @Transactional
    public void revokeAll(UUID userId, Instant now) {
        sessionRepository.findAllByUserIdAndStatus(userId, LoginSessionStatus.ACTIVE).forEach(session -> {
            session.revoke(LoginSessionStatus.REVOKED, now, "LOGOUT_ALL");
            sessionRepository.save(session);
            refreshRepository.findAllBySessionId(session.id()).forEach(value -> { value.revoke(now, "LOGOUT_ALL"); refreshRepository.save(value); });
        });
    }

    @Override
    @Transactional
    public CleanupResult purge(Instant now, Instant retentionCutoff) {
        int refreshTokens = refreshRepository.deleteRetainedExpired(retentionCutoff);
        int sessions = sessionRepository.deleteRetainedInactive(now, retentionCutoff);
        return new CleanupResult(refreshTokens, sessions);
    }
}
