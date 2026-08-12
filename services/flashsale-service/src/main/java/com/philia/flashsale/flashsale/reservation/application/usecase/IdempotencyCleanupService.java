package com.philia.flashsale.flashsale.reservation.application.usecase;

import com.philia.flashsale.flashsale.reservation.application.port.out.DeleteExpiredIdempotencyPort;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Bounded, retention-only cleanup. No durable business/audit record is deleted. */
public final class IdempotencyCleanupService {
    private static final int BATCH_SIZE = 100;
    private final DeleteExpiredIdempotencyPort cleanup;
    private final Clock clock;

    public IdempotencyCleanupService(DeleteExpiredIdempotencyPort cleanup) {
        this(cleanup, Clock.systemUTC());
    }

    public IdempotencyCleanupService(DeleteExpiredIdempotencyPort cleanup, Clock clock) {
        this.cleanup = Objects.requireNonNull(cleanup, "cleanup");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public int cleanExpired() {
        return cleanExpired(clock.instant());
    }

    public int cleanExpired(Instant now) {
        return cleanup.deleteExpired(now, BATCH_SIZE);
    }
}
