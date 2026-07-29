package com.philia.flashsale.authentication.session.application.refresh;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountRole;
import com.philia.flashsale.authentication.account.domain.AccountStatus;
import com.philia.flashsale.authentication.security.token.SecureRefreshCredentialAdapter;
import com.philia.flashsale.authentication.security.token.Sha256RefreshCredentialDigestAdapter;
import com.philia.flashsale.authentication.session.domain.SessionFailure;
import org.junit.jupiter.api.Test;

class RefreshSessionServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-28T00:00:00Z");

    @Test
    void raisesReuseFailureOnlyAfterPersistenceReturnsItsCommittedCompromiseOutcome() {
        RotateRefreshCredentialPort rotation = mock(RotateRefreshCredentialPort.class);
        when(rotation.rotate(any(), any(), any()))
                .thenReturn(new RotateRefreshCredentialPort.RefreshReuseDetected());
        RefreshSessionService service = service(rotation, mock(IssueRefreshedCredentialsPort.class));

        assertThatThrownBy(() -> service.refresh(new RefreshSessionCommand("reused-credential")))
                .isInstanceOf(SessionFailure.class)
                .extracting(error -> ((SessionFailure) error).code())
                .isEqualTo("AUTH_REFRESH_REUSE_DETECTED");
    }

    @Test
    void translatesPostCommitSigningFailureToAnExplicitAvailabilityFailure() {
        RotateRefreshCredentialPort rotation = mock(RotateRefreshCredentialPort.class);
        IssueRefreshedCredentialsPort credentials = mock(IssueRefreshedCredentialsPort.class);
        UUID sessionId = UUID.randomUUID();
        when(rotation.rotate(any(), any(), any())).thenReturn(new RotateRefreshCredentialPort.RefreshRotation(
                account(), sessionId, NOW.plusSeconds(3600)));
        when(credentials.issue(any(), any(), any())).thenThrow(new IllegalStateException("signing failed"));
        RefreshSessionService service = service(rotation, credentials);

        assertThatThrownBy(() -> service.refresh(new RefreshSessionCommand("current-credential")))
                .isInstanceOf(RefreshCredentialIssuanceUnavailableException.class)
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    private RefreshSessionService service(RotateRefreshCredentialPort rotation,
                                          IssueRefreshedCredentialsPort credentials) {
        SecureRefreshCredentialAdapter generator = mock(SecureRefreshCredentialAdapter.class);
        when(generator.generate()).thenReturn("successor-credential");
        return new RefreshSessionService(rotation, credentials, generator,
                new Sha256RefreshCredentialDigestAdapter(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Account account() {
        return Account.restore(UUID.randomUUID(), "user@example.test", "user@example.test", "user", "user",
                "argon-hash", AccountRole.ROLE_USER, AccountStatus.ACTIVE, null, null, NOW, NOW);
    }
}
