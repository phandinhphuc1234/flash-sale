package com.philia.flashsale.authentication.session.application.login;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.authentication.security.token.SecureRefreshCredentialAdapter;
import com.philia.flashsale.authentication.security.token.Sha256RefreshCredentialDigestAdapter;
import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountFailure;

/** Login orchestration: throttle, prove, persist atomically, then issue credentials. */
public class AuthenticateAccountService implements AuthenticateAccountUseCase {
    private final LoadAccountForAuthenticationPort accountPort;
    private final PasswordProofPort passwordProofPort;
    private final LoginThrottlePort throttlePort;
    private final PersistSuccessfulLoginPort persistencePort;
    private final IssueLoginCredentialsPort credentialsPort;
    private final SecureRefreshCredentialAdapter refreshGenerator;
    private final Sha256RefreshCredentialDigestAdapter digestAdapter;
    private final Clock clock;

    public AuthenticateAccountService(LoadAccountForAuthenticationPort accountPort, PasswordProofPort passwordProofPort,
                                      LoginThrottlePort throttlePort, PersistSuccessfulLoginPort persistencePort,
                                      IssueLoginCredentialsPort credentialsPort, SecureRefreshCredentialAdapter refreshGenerator,
                                      Sha256RefreshCredentialDigestAdapter digestAdapter, Clock clock) {
        this.accountPort = accountPort; this.passwordProofPort = passwordProofPort; this.throttlePort = throttlePort;
        this.persistencePort = persistencePort; this.credentialsPort = credentialsPort; this.refreshGenerator = refreshGenerator;
        this.digestAdapter = digestAdapter; this.clock = clock;
    }

    @Override
    public AuthenticationResult authenticate(AuthenticateAccountCommand command) {
        if (command == null || command.login() == null || command.login().isBlank() || command.password() == null) {
            throw new AccountFailure("AUTH_VALIDATION_FAILED", "Login and password are required");
        }
        String login = Account.normalizeRequired(command.login(), "login");
        throttlePort.beforeAttempt(login);
        Account account = accountPort.load(login).orElse(null);
        boolean matches;
        if (account == null) {
            passwordProofPort.verifyDummy(command.password());
            matches = false;
        } else {
            matches = passwordProofPort.matches(command.password(), account.passwordHash());
        }
        if (!matches || !account.canAuthenticate(Instant.now(clock))) {
            throttlePort.recordFailure(login);
            throw new AccountFailure("AUTH_INVALID_CREDENTIALS", "Invalid credentials");
        }
        throttlePort.beforeCommit(login);
        Instant now = Instant.now(clock);
        String rawRefresh = refreshGenerator.generate();
        String digest = digestAdapter.digest(rawRefresh);
        UUID sessionId = persistencePort.persist(account, digest, bounded(command.deviceName(), 150), bounded(command.userAgent(), 512), command.directIp(), now);
        String accessToken = credentialsPort.issueAccessToken(account, sessionId, now);
        return new AuthenticationResult("Bearer", accessToken, 900, rawRefresh, 604800);
    }

    private String bounded(String value, int max) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
