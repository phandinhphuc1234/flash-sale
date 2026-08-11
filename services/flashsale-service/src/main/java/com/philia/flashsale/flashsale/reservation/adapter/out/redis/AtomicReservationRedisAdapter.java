package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import com.philia.flashsale.flashsale.reservation.application.port.out.ExecuteAtomicReservationPort;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationDecisionResult;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

/** Executes the approved reservation Lua script without a non-atomic Java fallback. */
public final class AtomicReservationRedisAdapter implements ExecuteAtomicReservationPort {
    private final StringRedisTemplate redis;
    private final RedisScript<List> script;
    private final ReservationLuaResultMapper resultMapper = new ReservationLuaResultMapper();

    public AtomicReservationRedisAdapter(StringRedisTemplate redis) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.script = script("redis/reservation/reserve-campaign-quota.lua");
    }

    @Override
    public ReservationDecisionResult execute(AtomicReservationRequest request) {
        var command = request.command();
        List<?> result = redis.execute(script,
                List.of(
                        ReservationRedisKeys.meta(command.campaignId()),
                        ReservationRedisKeys.stock(command.campaignId()),
                        ReservationRedisKeys.item(command.campaignId(), command.variantId()),
                        ReservationRedisKeys.userQuantity(command.campaignId(), command.userId()),
                        ReservationRedisKeys.idempotency(command.campaignId(), command.userId(),
                                request.idempotencyKeyHash()),
                        ReservationRedisKeys.reservation(command.campaignId(), request.reservationId()),
                        ReservationRedisKeys.expirations(),
                        ReservationRedisKeys.handoff()),
                Long.toString(request.acceptedAt().toEpochMilli()), command.campaignId().toString(),
                command.variantId().toString(), command.userId().toString(), Long.toString(command.quantity()),
                request.idempotencyKeyHash(), request.requestHash(), request.purchaseRequestId().toString(),
                request.reservationId().toString(), request.eventId().toString(),
                Long.toString(request.acceptedAt().toEpochMilli()), Long.toString(request.expiresAt().toEpochMilli()),
                Long.toString(request.retainedUntil().toEpochMilli()), value(command.traceparent()),
                value(command.tracestate()));
        return resultMapper.map(result);
    }

    private RedisScript<List> script(String path) {
        DefaultRedisScript<List> loaded = new DefaultRedisScript<>();
        loaded.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        loaded.setResultType(List.class);
        return loaded;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
