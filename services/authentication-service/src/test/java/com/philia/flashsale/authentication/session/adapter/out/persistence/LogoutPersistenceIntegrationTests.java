package com.philia.flashsale.authentication.session.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.philia.flashsale.authentication.session.domain.LoginSessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;

class LogoutPersistenceIntegrationTests extends AuthenticationPostgreSqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-29T00:00:00Z");

    @Test
    void currentLogoutRevokesTheOwningSessionAndWholeCredentialChainIdempotently() {
        SessionFixture fixture = createRotatedCredentialChain();

        persistenceAdapter.revokeCurrent(hash('b'), NOW);
        persistenceAdapter.revokeCurrent(hash('b'), NOW.plusSeconds(1));

        LoginSessionJpaEntity session = sessions.findById(fixture.sessionId()).orElseThrow();
        List<RefreshCredentialJpaEntity> chain = refreshCredentials.findAllBySessionId(fixture.sessionId());
        assertThat(session.status()).isEqualTo(LoginSessionStatus.REVOKED);
        assertThat(session.revokedAt()).isEqualTo(NOW);
        assertThat(session.revokeReason()).isEqualTo("LOGOUT_CURRENT");
        assertThat(chain).hasSize(2).allMatch(token -> token.revokedAt().equals(NOW));
    }

    @Test
    void logoutAllAtomicallyRevokesEveryActiveSessionOwnedByTheSubject() {
        SessionFixture first = createCurrentSession(hash('c'), NOW);
        SessionFixture second = createAnotherSessionFor(first.userId(), hash('d'));

        persistenceAdapter.revokeAll(first.userId(), NOW);
        persistenceAdapter.revokeAll(first.userId(), NOW.plusSeconds(1));

        assertThat(sessions.findAllByUserIdAndStatus(first.userId(), LoginSessionStatus.ACTIVE)).isEmpty();
        assertThat(List.of(first.sessionId(), second.sessionId()))
                .allSatisfy(sessionId -> {
                    LoginSessionJpaEntity session = sessions.findById(sessionId).orElseThrow();
                    assertThat(session.status()).isEqualTo(LoginSessionStatus.REVOKED);
                    assertThat(session.revokedAt()).isEqualTo(NOW);
                    assertThat(refreshCredentials.findAllBySessionId(sessionId))
                            .allMatch(token -> token.revokedAt().equals(NOW));
                });
    }

    @Test
    void databaseFailureDuringLogoutRollsBackSessionAndCredentialRevocationTogether() {
        SessionFixture fixture = createCurrentSession(hash('e'), NOW);
        installRefreshRevocationFailureTrigger();
        try {
            assertThatThrownBy(() -> persistenceAdapter.revokeCurrent(fixture.tokenHash(), NOW))
                    .isInstanceOf(DataAccessException.class);
        } finally {
            removeRefreshRevocationFailureTrigger();
        }

        LoginSessionJpaEntity session = sessions.findById(fixture.sessionId()).orElseThrow();
        RefreshCredentialJpaEntity refresh = refreshCredentials.findById(fixture.refreshId()).orElseThrow();
        assertThat(session.status()).isEqualTo(LoginSessionStatus.ACTIVE);
        assertThat(session.revokedAt()).isNull();
        assertThat(session.revokeReason()).isNull();
        assertThat(refresh.revokedAt()).isNull();
        assertThat(refresh.revokeReason()).isNull();
    }

    private SessionFixture createRotatedCredentialChain() {
        SessionFixture fixture = createCurrentSession(hash('a'), NOW);
        persistenceAdapter.rotate(hash('a'), hash('b'), NOW.minusSeconds(1));
        return fixture;
    }

    private SessionFixture createAnotherSessionFor(UUID userId, String tokenHash) {
        UUID sessionId = UUID.randomUUID();
        UUID refreshId = UUID.randomUUID();
        sessions.saveAndFlush(new LoginSessionJpaEntity(
                sessionId,
                userId,
                LoginSessionStatus.ACTIVE,
                "integration-test-2",
                "JUnit",
                "127.0.0.1",
                NOW.minusSeconds(1800),
                NOW.minusSeconds(30),
                NOW.plusSeconds(86_400),
                null,
                null));
        refreshCredentials.saveAndFlush(new RefreshCredentialJpaEntity(
                refreshId,
                sessionId,
                tokenHash,
                null,
                null,
                NOW.minusSeconds(300),
                NOW.plusSeconds(3600),
                null,
                null,
                null));
        return new SessionFixture(userId, sessionId, refreshId, tokenHash);
    }

    private void installRefreshRevocationFailureTrigger() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fail_test_refresh_revocation()
                RETURNS trigger AS $$
                BEGIN
                    IF NEW.revoke_reason = 'LOGOUT_CURRENT' THEN
                        RAISE EXCEPTION 'simulated refresh revocation failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_test_refresh_revocation_trigger
                BEFORE UPDATE ON refresh_tokens
                FOR EACH ROW EXECUTE FUNCTION fail_test_refresh_revocation()
                """);
    }

    private void removeRefreshRevocationFailureTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS fail_test_refresh_revocation_trigger ON refresh_tokens");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_test_refresh_revocation()");
    }
}
