package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.port.out.ReconcileReservationConfirmationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scripting.support.ResourceScriptSource;

/** Redis adapter for the idempotent confirmation projection. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class ReservationConfirmationRedisAdapter implements ReconcileReservationConfirmationPort {
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<String> script;
    private final FlashSaleObservability observability;

    public ReservationConfirmationRedisAdapter(StringRedisTemplate redis) {
        this(redis, FlashSaleObservability.noop());
    }

    @Autowired
    public ReservationConfirmationRedisAdapter(StringRedisTemplate redis,
            FlashSaleObservability observability) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.observability = Objects.requireNonNull(observability, "observability");
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("redis/reservation/confirm-reservation.lua")));
        this.script.setResultType(String.class);
    }

    @Override
    public void confirm(ConfirmReservationCommand command, ReservationConfirmationResult result) {
        String redisResult = observability.observe(FlashSaleObservability.Operation.REDIS_LUA,
                () -> redis.execute(script,
                        List.of(ReservationRedisKeys.reservation(result.campaignId(), command.reservationId()),
                                ReservationRedisKeys.expirations()),
                        command.reservationId().toString()));
        if (redisResult == null || "NOT_FOUND".equals(redisResult) || "NOT_CONFIRMABLE".equals(redisResult)) {
            throw new IllegalStateException("Redis reservation confirmation was not applied: " + redisResult);
        }
    }
}
