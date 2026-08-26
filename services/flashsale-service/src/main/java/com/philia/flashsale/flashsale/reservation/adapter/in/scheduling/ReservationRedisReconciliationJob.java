package com.philia.flashsale.flashsale.reservation.adapter.in.scheduling;

import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bounded single-process lease: an in-flight pass is never allowed to overlap itself. */
@Component
@ConditionalOnExpression("'${flashsale.runtime.enabled:true}' == 'true' && '${flashsale.runtime.reconciliation-enabled:true}' == 'true'")
public final class ReservationRedisReconciliationJob {
    private static final Logger LOG = LoggerFactory.getLogger(ReservationRedisReconciliationJob.class);
    private final ReservationReconciliationService reconciliation;
    private final FlashSaleObservability observability;
    private boolean running;

    public ReservationRedisReconciliationJob(ReservationReconciliationService reconciliation,
            FlashSaleObservability observability) {
        this.reconciliation = reconciliation;
        this.observability = observability;
    }

    @Scheduled(fixedDelayString = "${flashsale.runtime.reconciliation-interval-ms:2000}")
    public synchronized void reconcile() {
        if (running) return;
        running = true;
        try {
            observability.observe(FlashSaleObservability.Operation.REDIS_RECONCILIATION,
                    () -> reconciliation.reconcileBatch(50));
        } catch (RuntimeException exception) {
            LOG.warn("flashsale_reservation_redis_reconciliation_failed exceptionType={}",
                    exception.getClass().getSimpleName());
        } finally {
            running = false;
        }
    }
}
