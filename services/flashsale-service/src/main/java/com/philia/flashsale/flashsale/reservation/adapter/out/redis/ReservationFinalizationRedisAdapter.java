package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationProjectionPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReconciliationCandidate;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

/** Replays durable confirmation/release Lua projections after a process crash or Redis outage. */
@Component
@ConditionalOnExpression("'${flashsale.runtime.enabled:true}' == 'true' && '${flashsale.runtime.reconciliation-enabled:true}' == 'true'")
public final class ReservationFinalizationRedisAdapter implements ReconcileReservationProjectionPort {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<String> confirmScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final FlashSaleObservability observability;

    public ReservationFinalizationRedisAdapter(StringRedisTemplate redis, FlashSaleObservability observability) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.observability = Objects.requireNonNull(observability, "observability");
        confirmScript = new DefaultRedisScript<>();
        confirmScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/reservation/confirm-reservation.lua")));
        confirmScript.setResultType(String.class);
        releaseScript = new DefaultRedisScript<>();
        releaseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/reservation/release-reservation.lua")));
        releaseScript.setResultType(Long.class);
    }

    @Override
    public void reconcile(ReservationReconciliationCandidate candidate) {
        if (candidate.status() == ReservationReconciliationCandidate.Status.CONFIRMED) {
            String result = observability.observe(FlashSaleObservability.Operation.REDIS_RECONCILIATION,
                    () -> redis.execute(confirmScript,
                            List.of(ReservationRedisKeys.reservation(candidate.campaignId(), candidate.reservationId()),
                                    ReservationRedisKeys.expirations()), candidate.reservationId().toString()));
            if (result == null || "NOT_FOUND".equals(result) || "NOT_CONFIRMABLE".equals(result)) {
                throw new IllegalStateException("durable confirmation projection was not reconciled: " + result);
            }
            return;
        }
        Long result = observability.observe(FlashSaleObservability.Operation.REDIS_RECONCILIATION,
                () -> redis.execute(releaseScript,
                        List.of(ReservationRedisKeys.reservation(candidate.campaignId(), candidate.reservationId()),
                                ReservationRedisKeys.stock(candidate.campaignId()),
                                ReservationRedisKeys.userQuantity(candidate.campaignId(), candidate.userId()),
                                ReservationRedisKeys.expirations()), candidate.variantId().toString(),
                        Long.toString(candidate.quantity()), candidate.reservationId().toString(),
                        candidate.status().name()));
        if (result != null && result == -1L) throw new IllegalStateException("durable release conflicts with confirmed Redis state");
    }
}
