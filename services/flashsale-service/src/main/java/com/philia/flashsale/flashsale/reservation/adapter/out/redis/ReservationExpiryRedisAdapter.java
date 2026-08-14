package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import com.philia.flashsale.flashsale.reservation.application.port.out.ReleaseExpiredQuotaPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationExpiryCandidate;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Executes only the approved atomic release script; repeat calls are harmless.
 */
public final class ReservationExpiryRedisAdapter implements ReleaseExpiredQuotaPort {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;
    private final FlashSaleObservability observability;

    public ReservationExpiryRedisAdapter(StringRedisTemplate redis) {
        this(redis, FlashSaleObservability.noop());
    }

    public ReservationExpiryRedisAdapter(StringRedisTemplate redis, FlashSaleObservability observability) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.observability = Objects.requireNonNull(observability, "observability");
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("redis/reservation/release-expired-reservation.lua")));
        this.script.setResultType(Long.class);
    }

    // Executes the Lua script to release expired reservations, stock, and user
    // quantity in Redis.
    // The script is idempotent, so repeat calls with the same candidate are
    // harmless.
    @Override
    public void release(ReservationExpiryCandidate candidate) {
        observability.observe(FlashSaleObservability.Operation.REDIS_LUA, () -> redis.execute(script, List.of(
                ReservationRedisKeys.reservation(candidate.campaignId(), candidate.reservationId()),
                ReservationRedisKeys.stock(candidate.campaignId()),
                ReservationRedisKeys.userQuantity(candidate.campaignId(), candidate.userId()),
                ReservationRedisKeys.expirations()),
                candidate.variantId().toString(), Long.toString(candidate.quantity()),
                candidate.reservationId().toString()));
    }
}
