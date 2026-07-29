package com.philia.flashsale.authentication.session.application.login;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.philia.flashsale.authentication.account.domain.AccountFailure;
import com.philia.flashsale.authentication.security.token.SecureRefreshCredentialAdapter;
import com.philia.flashsale.authentication.security.token.Sha256RefreshCredentialDigestAdapter;
import org.junit.jupiter.api.Test;

class AuthenticateAccountServiceTests {

    @Test
    void unknownAccountStillPerformsTheConfiguredDummyArgon2Proof() {
        LoadAccountForAuthenticationPort accounts = mock(LoadAccountForAuthenticationPort.class);
        PasswordProofPort passwordProof = mock(PasswordProofPort.class);
        LoginThrottlePort throttle = mock(LoginThrottlePort.class);
        PersistSuccessfulLoginPort persistence = mock(PersistSuccessfulLoginPort.class);
        IssueLoginCredentialsPort credentials = mock(IssueLoginCredentialsPort.class);
        SecureRefreshCredentialAdapter refreshGenerator = mock(SecureRefreshCredentialAdapter.class);
        Sha256RefreshCredentialDigestAdapter digest = mock(Sha256RefreshCredentialDigestAdapter.class);
        when(accounts.load("missing@example.test")).thenReturn(Optional.empty());

        AuthenticateAccountService service = new AuthenticateAccountService(
                accounts, passwordProof, throttle, persistence, credentials, refreshGenerator, digest,
                Clock.fixed(Instant.parse("2026-07-28T00:00:00Z"), ZoneOffset.UTC));

        assertThatThrownBy(() -> service.authenticate(new AuthenticateAccountCommand(
                " Missing@Example.Test ", "not-the-password", null, null, "127.0.0.1")))
                .isInstanceOf(AccountFailure.class)
                .extracting(error -> ((AccountFailure) error).code())
                .isEqualTo("AUTH_INVALID_CREDENTIALS");

        verify(passwordProof).verifyDummy("not-the-password");
        verify(passwordProof, never()).matches("not-the-password", null);
        verify(throttle).recordFailure("missing@example.test");
    }
}
