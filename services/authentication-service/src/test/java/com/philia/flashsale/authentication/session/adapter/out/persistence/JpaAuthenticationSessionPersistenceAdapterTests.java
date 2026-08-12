package com.philia.flashsale.authentication.session.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.philia.flashsale.authentication.account.adapter.out.persistence.AccountJpaRepository;
import com.philia.flashsale.authentication.session.application.refresh.RotateRefreshCredentialPort;
import com.philia.flashsale.authentication.session.domain.LoginSessionStatus;
import org.junit.jupiter.api.Test;

class JpaAuthenticationSessionPersistenceAdapterTests {

    @Test
    void refreshReuseReturnsACommittedOutcomeAfterCompromisingTheWholeSessionChain() {
        AccountJpaRepository accounts = mock(AccountJpaRepository.class);
        LoginSessionJpaRepository sessions = mock(LoginSessionJpaRepository.class);
        RefreshCredentialJpaRepository refreshTokens = mock(RefreshCredentialJpaRepository.class);
        JpaAuthenticationSessionPersistenceAdapter adapter =
                new JpaAuthenticationSessionPersistenceAdapter(accounts, sessions, refreshTokens);

        Instant now = Instant.parse("2026-07-28T00:00:00Z");
        UUID sessionId = UUID.randomUUID();
        LoginSessionJpaEntity session = new LoginSessionJpaEntity(
                sessionId, UUID.randomUUID(), LoginSessionStatus.ACTIVE, null, null, "127.0.0.1",
                now.minusSeconds(3600), now.minusSeconds(60), now.plusSeconds(3600), null, null);
        RefreshCredentialJpaEntity reused = new RefreshCredentialJpaEntity(
                UUID.randomUUID(), sessionId, "reused-hash", null, UUID.randomUUID(),
                now.minusSeconds(600), now.plusSeconds(600), now.minusSeconds(300), null, null);
        RefreshCredentialJpaEntity successor = new RefreshCredentialJpaEntity(
                UUID.randomUUID(), sessionId, "successor-hash", reused.id(), null,
                now.minusSeconds(300), now.plusSeconds(900), null, null, null);
        when(refreshTokens.findByTokenHash("reused-hash")).thenReturn(Optional.of(reused));
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));
        when(refreshTokens.findAllBySessionId(sessionId)).thenReturn(List.of(reused, successor));

        RotateRefreshCredentialPort.RotationOutcome outcome =
                adapter.rotate("reused-hash", "unused-successor-hash", now);

        assertThat(outcome).isInstanceOf(RotateRefreshCredentialPort.RefreshReuseDetected.class);
        assertThat(session.status()).isEqualTo(LoginSessionStatus.COMPROMISED);
        assertThat(session.revokedAt()).isEqualTo(now);
        assertThat(reused.revokedAt()).isEqualTo(now);
        assertThat(successor.revokedAt()).isEqualTo(now);
        verify(sessions).save(session);
        verify(refreshTokens).save(reused);
        verify(refreshTokens).save(successor);
    }
}
