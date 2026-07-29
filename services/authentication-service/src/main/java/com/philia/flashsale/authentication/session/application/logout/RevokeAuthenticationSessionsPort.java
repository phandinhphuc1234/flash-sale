package com.philia.flashsale.authentication.session.application.logout;

import java.time.Instant;
import java.util.UUID;

/** Outbound capability for current-chain and subject-owned bulk revocation. */
public interface RevokeAuthenticationSessionsPort {
    void revokeCurrent(String refreshHash, Instant now);
    void revokeAll(UUID userId, Instant now);
}
