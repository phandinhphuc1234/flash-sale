package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.reservation.application.usecase.IdempotencyCleanupService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IdempotencyRetentionIntegrationTests {
    @Test
    void cleanupUsesOnlyTheApprovedBoundedBatchAndLeavesAuditOwnershipToPersistence() {
        AtomicInteger batch = new AtomicInteger();
        IdempotencyCleanupService cleanup = new IdempotencyCleanupService((now, requestedBatch) -> {
            batch.set(requestedBatch);
            return requestedBatch;
        }, Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC));

        assertThat(cleanup.cleanExpired()).isEqualTo(100);
        assertThat(batch).hasValue(100);
    }
}
