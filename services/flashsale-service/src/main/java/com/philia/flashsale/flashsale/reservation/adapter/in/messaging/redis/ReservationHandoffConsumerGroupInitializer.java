package com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis;

import com.philia.flashsale.flashsale.configuration.RedisHotPathProperties;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Creates the durable-acceptance consumer group without replacing an existing group. */
@Component
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
public final class ReservationHandoffConsumerGroupInitializer {
    private final StringRedisTemplate redis;
    private final RedisHotPathProperties properties;

    public ReservationHandoffConsumerGroupInitializer(StringRedisTemplate redis,
            RedisHotPathProperties properties) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    public void initialize() {
        try {
            redis.execute((RedisConnection connection) -> connection.xGroupCreate(
                    bytes(properties.handoffStream()), properties.consumerGroup(), ReadOffset.from("0-0"), true));
        } catch (DataAccessException exception) {
            if (!isBusyGroup(exception)) {
                throw exception;
            }
        }
    }

    private boolean isBusyGroup(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains("BUSYGROUP")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
