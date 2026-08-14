package com.philia.flashsale.flashsale.reservation.adapter.out.redis;

import com.philia.flashsale.flashsale.configuration.RedisHotPathProperties;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;
import com.philia.flashsale.flashsale.reservation.application.port.out.AcknowledgeReservationHandoffPort;
import io.lettuce.core.Consumer;
import io.lettuce.core.StreamMessage;
import io.lettuce.core.XAutoClaimArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.models.stream.ClaimedMessages;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

/** Reads new/reclaimed handoff work and atomically acknowledges terminal entries. */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public final class ReservationHandoffRedisAdapter implements AcknowledgeReservationHandoffPort {
    private final StringRedisTemplate redis;
    private final RedisHotPathProperties properties;
    private final String consumerName;
    private final RedisScript<Long> acknowledgeScript;
    private final FlashSaleObservability observability;
    private String reclaimCursor = "0-0";

    public ReservationHandoffRedisAdapter(StringRedisTemplate redis, RedisHotPathProperties properties) {
        this(redis, properties, defaultConsumerName());
    }

    public ReservationHandoffRedisAdapter(StringRedisTemplate redis, RedisHotPathProperties properties,
            String consumerName) {
        this(redis, properties, consumerName, FlashSaleObservability.noop());
    }

    @Autowired
    public ReservationHandoffRedisAdapter(StringRedisTemplate redis, RedisHotPathProperties properties,
            String consumerName, FlashSaleObservability observability) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.observability = Objects.requireNonNull(observability, "observability");
        if (consumerName == null || consumerName.isBlank()) {
            throw new IllegalArgumentException("consumerName must not be blank");
        }
        this.consumerName = consumerName;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("redis/reservation/ack-delete-handoff.lua")));
        script.setResultType(Long.class);
        this.acknowledgeScript = script;
    }

    public List<MapRecord<String, String, String>> readNewEntries() {
        return observability.observe(FlashSaleObservability.Operation.REDIS_HANDOFF, () -> {
            List<MapRecord<String, String, String>> records = redis.<String, String>opsForStream().read(
                org.springframework.data.redis.connection.stream.Consumer.from(properties.consumerGroup(), consumerName),
                StreamReadOptions.empty().block(properties.pollTimeout()).count(properties.batchSize()),
                StreamOffset.create(properties.handoffStream(), ReadOffset.lastConsumed()));
            return records == null ? List.of() : records;
        });
    }

    public List<MapRecord<String, String, String>> reclaimPendingEntries() {
        return observability.observe(FlashSaleObservability.Operation.REDIS_HANDOFF, () -> {
            ClaimedMessages<byte[], byte[]> claimed = redis.execute((RedisConnection connection) -> {
            @SuppressWarnings("unchecked")
            RedisAsyncCommands<byte[], byte[]> commands =
                    (RedisAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
            XAutoClaimArgs<byte[]> args = new XAutoClaimArgs<byte[]>()
                    .consumer(Consumer.from(bytes(properties.consumerGroup()), bytes(consumerName)))
                    .minIdleTime(properties.reclaimIdle())
                    .count(properties.batchSize())
                    .startId(reclaimCursor);
            try {
                return commands.xautoclaim(bytes(properties.handoffStream()), args).get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Redis Stream reclaim was interrupted", exception);
            } catch (java.util.concurrent.ExecutionException exception) {
                throw new IllegalStateException("Redis Stream reclaim failed", exception.getCause());
            }
            });
            if (claimed == null) {
                return List.of();
            }
            reclaimCursor = claimed.getId();
            return claimed.getMessages().stream().map(this::mapMessage).toList();
        });
    }

    @Override
    public void acknowledge(java.util.UUID reservationId, String handoffEntryId) {
        Objects.requireNonNull(reservationId, "reservationId");
        Objects.requireNonNull(handoffEntryId, "handoffEntryId");
        observability.observe(FlashSaleObservability.Operation.REDIS_HANDOFF, () -> redis.execute(
                acknowledgeScript, List.of(properties.handoffStream()),
                properties.consumerGroup(), handoffEntryId));
    }

    String consumerName() {
        return consumerName;
    }

    private MapRecord<String, String, String> mapMessage(StreamMessage<byte[], byte[]> message) {
        java.util.LinkedHashMap<String, String> body = new java.util.LinkedHashMap<>();
        message.getBody().forEach((key, value) -> body.put(text(key), text(value)));
        return MapRecord.<String, String, String>create(properties.handoffStream(), body)
                .withId(RecordId.of(message.getId()));
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String text(byte[] value) {
        return new String(value, StandardCharsets.UTF_8);
    }

    private static String defaultConsumerName() {
        String host = System.getenv("HOSTNAME");
        return (host == null || host.isBlank()) ? "flashsale-consumer" : host;
    }
}
