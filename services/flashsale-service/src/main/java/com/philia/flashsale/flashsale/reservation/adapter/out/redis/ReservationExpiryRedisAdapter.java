package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import com.philia.flashsale.flashsale.reservation.application.port.out.ReleaseExpiredQuotaPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationExpiryCandidate;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/** Executes only the approved atomic release script; repeat calls are harmless. */
public final class ReservationExpiryRedisAdapter implements ReleaseExpiredQuotaPort {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public ReservationExpiryRedisAdapter(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("redis/reservation/release-expired-reservation.lua")));
        this.script.setResultType(Long.class);
    }

    @Override
    public void release(ReservationExpiryCandidate candidate) {
        redis.execute(script, List.of(
                ReservationRedisKeys.reservation(candidate.campaignId(), candidate.reservationId()),
                ReservationRedisKeys.stock(candidate.campaignId()),
                ReservationRedisKeys.userQuantity(candidate.campaignId(), candidate.userId()),
                ReservationRedisKeys.expirations()),
                candidate.variantId().toString(), Long.toString(candidate.quantity()),
                candidate.reservationId().toString());
    }
}
