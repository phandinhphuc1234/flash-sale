package com.philia.flashsale.authentication.cleanup.adapter.in.scheduling;

import com.philia.flashsale.authentication.cleanup.application.CleanupExpiredSessionsUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Invokes retention cleanup once per configured day; the operation itself is idempotent. */
@Component
@ConditionalOnBean(CleanupExpiredSessionsUseCase.class)
/** Cron-driven inbound adapter that invokes retention cleanup. */
public class ExpiredAuthenticationStateCleanupScheduler {
    private final CleanupExpiredSessionsUseCase cleanup;
    private final ObjectProvider<AuthenticationCleanupMetrics> metrics;

    public ExpiredAuthenticationStateCleanupScheduler(CleanupExpiredSessionsUseCase cleanup,
            ObjectProvider<AuthenticationCleanupMetrics> metrics) {
        this.cleanup = cleanup;
        this.metrics = metrics;
    }

    @Scheduled(cron = "${flashsale.authentication.cleanup-cron:0 0 3 * * *}")
    public void purgeExpiredState() {
        try {
            cleanup.cleanup();
            AuthenticationCleanupMetrics value = metrics.getIfAvailable();
            if (value != null) value.run();
        } catch (RuntimeException exception) {
            AuthenticationCleanupMetrics value = metrics.getIfAvailable();
            if (value != null) value.failure();
            throw exception;
        }
    }
}
