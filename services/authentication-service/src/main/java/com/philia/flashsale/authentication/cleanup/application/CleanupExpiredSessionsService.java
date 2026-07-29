package com.philia.flashsale.authentication.cleanup.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Keeps retention policy in the application layer while leaving deletion mechanics in persistence.
 * Running the command repeatedly is safe because deletes are idempotent.
 */
/** Retention-policy orchestration; it calculates the cutoff and delegates deletion. */
public final class CleanupExpiredSessionsService implements CleanupExpiredSessionsUseCase {
    private final PurgeExpiredAuthenticationStatePort purgePort;
    private final Clock clock;
    private final Duration retention;

    public CleanupExpiredSessionsService(PurgeExpiredAuthenticationStatePort purgePort,
            Clock clock, Duration retention) {
        this.purgePort = purgePort;
        this.clock = clock;
        this.retention = retention;
    }

    @Override
    public PurgeExpiredAuthenticationStatePort.CleanupResult cleanup() {
        Instant now = Instant.now(clock);
        return purgePort.purge(now, now.minus(retention));
    }
}
