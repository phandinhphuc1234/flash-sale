package com.philia.flashsale.authentication.session.application.refresh;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.philia.flashsale.authentication.security.token.SecureRefreshCredentialAdapter;
import com.philia.flashsale.authentication.security.token.Sha256RefreshCredentialDigestAdapter;
import com.philia.flashsale.authentication.session.domain.SessionFailure;

/** Refresh orchestration that hashes input, delegates locked rotation, then signs after commit. */
public class RefreshSessionService implements RefreshSessionUseCase {
    private final RotateRefreshCredentialPort rotationPort;
    private final IssueRefreshedCredentialsPort credentialsPort;
    private final SecureRefreshCredentialAdapter generator;
    private final Sha256RefreshCredentialDigestAdapter digestAdapter;
    private final Clock clock;

    public RefreshSessionService(RotateRefreshCredentialPort rotationPort, IssueRefreshedCredentialsPort credentialsPort,
                                 SecureRefreshCredentialAdapter generator, Sha256RefreshCredentialDigestAdapter digestAdapter,
                                 Clock clock) {
        this.rotationPort = rotationPort; this.credentialsPort = credentialsPort; this.generator = generator;
        this.digestAdapter = digestAdapter; this.clock = clock;
    }

    @Override
    public RefreshSessionResult refresh(RefreshSessionCommand command) {
        if (command == null || command.rawCredential() == null || command.rawCredential().isBlank()) {
            throw new SessionFailure("AUTH_REFRESH_TOKEN_INVALID", "Refresh credential is invalid");
        }
        Instant now = Instant.now(clock);
        String rawSuccessor = generator.generate();
        RotateRefreshCredentialPort.RotationOutcome outcome = rotationPort.rotate(
                digestAdapter.digest(command.rawCredential()), digestAdapter.digest(rawSuccessor), now);
        if (outcome instanceof RotateRefreshCredentialPort.RefreshReuseDetected) {
            // The persistence port returned normally, so its transaction committed the compromise first.
            throw new SessionFailure("AUTH_REFRESH_REUSE_DETECTED", "Refresh credential was already used");
        }
        RotateRefreshCredentialPort.RefreshRotation rotation =
                (RotateRefreshCredentialPort.RefreshRotation) outcome;
        String accessToken;
        try {
            accessToken = credentialsPort.issue(rotation.account(), rotation.sessionId(), now);
        } catch (RuntimeException exception) {
            throw new RefreshCredentialIssuanceUnavailableException(exception);
        }
        long maxAge = Math.max(1, Duration.between(now, rotation.successorExpiresAt()).toSeconds());
        return new RefreshSessionResult(accessToken, rawSuccessor, 900, maxAge);
    }
}
