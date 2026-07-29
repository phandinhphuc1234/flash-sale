package com.philia.flashsale.authentication.session.application.logout;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.authentication.security.token.Sha256RefreshCredentialDigestAdapter;

/** Logout orchestration that keeps cookie hashing and subject ownership outside persistence details. */
public class LogoutSessionService implements LogoutSessionUseCase, LogoutAllSessionsUseCase {
    private final RevokeAuthenticationSessionsPort revokePort;
    private final Sha256RefreshCredentialDigestAdapter digestAdapter;
    private final Clock clock;

    public LogoutSessionService(RevokeAuthenticationSessionsPort revokePort,
                                Sha256RefreshCredentialDigestAdapter digestAdapter, Clock clock) {
        this.revokePort = revokePort; this.digestAdapter = digestAdapter; this.clock = clock;
    }

    @Override
    public void logoutCurrent(LogoutCurrentSessionCommand command) {
        if (command == null || command.rawCredential() == null || command.rawCredential().isBlank()) return;
        revokePort.revokeCurrent(digestAdapter.digest(command.rawCredential()), Instant.now(clock));
    }

    @Override
    public void logoutAll(LogoutAllSessionsCommand command) {
        if (command == null || command.userId() == null) return;
        revokePort.revokeAll(command.userId(), Instant.now(clock));
    }
}
