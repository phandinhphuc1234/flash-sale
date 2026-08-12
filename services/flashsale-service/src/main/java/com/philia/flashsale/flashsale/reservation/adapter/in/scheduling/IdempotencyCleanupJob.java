package com.philia.flashsale.flashsale.reservation.adapter.in.scheduling;

import com.philia.flashsale.flashsale.reservation.application.usecase.IdempotencyCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

// Cleanup job for expired idempotency keys. 
// Runs at a fixed interval to remove expired keys from the system.
@Component
@ConditionalOnBean(IdempotencyCleanupService.class)
public final class IdempotencyCleanupJob {
    private static final Logger LOG = LoggerFactory.getLogger(IdempotencyCleanupJob.class);
    private final IdempotencyCleanupService cleanup;

    public IdempotencyCleanupJob(IdempotencyCleanupService cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(fixedDelay = 60000L)
    public void cleanExpiredIdempotency() {
        try {
            cleanup.cleanExpired();
        } catch (RuntimeException exception) {
            LOG.warn("flashsale_idempotency_cleanup_failed exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
