package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationReleasePort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

/** Reconciles one durable release into Redis using an idempotent Lua script. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class ReservationReleaseRedisAdapter implements ReconcileReservationReleasePort {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;
    private final FlashSaleObservability observability;
    public ReservationReleaseRedisAdapter(StringRedisTemplate redis) { this(redis, FlashSaleObservability.noop()); }
    @Autowired
    public ReservationReleaseRedisAdapter(StringRedisTemplate redis, FlashSaleObservability observability) {
        this.redis = Objects.requireNonNull(redis, "redis"); this.observability = Objects.requireNonNull(observability, "observability");
        this.script = new DefaultRedisScript<>(); this.script.setScriptSource(new ResourceScriptSource(new ClassPathResource("redis/reservation/release-reservation.lua"))); this.script.setResultType(Long.class);
    }
    @Override public void release(ReleaseReservationCommand command, ReservationReleaseResult result) {
        Long applied = observability.observe(FlashSaleObservability.Operation.REDIS_LUA, () -> redis.execute(script,
                List.of(ReservationRedisKeys.reservation(result.campaignId(), result.reservationId()), ReservationRedisKeys.stock(result.campaignId()),
                        ReservationRedisKeys.userQuantity(result.campaignId(), result.userId()), ReservationRedisKeys.expirations()),
                result.variantId().toString(), Long.toString(result.quantity()), result.reservationId().toString(),
                result.status() == ReservationReleaseResult.Status.RELEASED || result.status() == ReservationReleaseResult.Status.ALREADY_RELEASED ? "RELEASED" : "EXPIRED"));
        if (applied != null && applied == -1L) throw new IllegalStateException("Redis reservation is already confirmed");
    }
}
