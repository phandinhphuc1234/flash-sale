package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.configuration.RedisHotPathProperties;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis.ReservationHandoffConsumerGroupInitializer;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationHandoffRedisAdapter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import io.lettuce.core.Consumer;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.XClaimArgs;
import java.nio.charset.StandardCharsets;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;

class ReservationHandoffAutoClaimIntegrationTests {
    private static final String STREAM = "fs:{hot}:handoff";
    private static final String GROUP = "flashsale-durable-acceptance-v1";

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @BeforeAll
    static void startRedis() {
        REDIS.start();
    }

    @AfterAll
    static void stopRedis() {
        REDIS.stop();
    }

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private RedisHotPathProperties properties;
    private ReservationHandoffRedisAdapter handoff;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        properties = new RedisHotPathProperties(STREAM, GROUP, Duration.ofMillis(1), 100, Duration.ofSeconds(30));
        handoff = new ReservationHandoffRedisAdapter(redis, properties, "worker-reclaimer");
        new ReservationHandoffConsumerGroupInitializer(redis, properties).initialize();
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void xautoclaimTransfersOnlyEntriesAtLeastThirtySecondsIdle() {
        String oldEntry = add("old");
        String youngEntry = add("young");
        assertThat(handoff.readNewEntries()).hasSize(2);

        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            @SuppressWarnings("unchecked")
            RedisAsyncCommands<byte[], byte[]> commands =
                    (RedisAsyncCommands<byte[], byte[]>) connection.getNativeConnection();
            try {
                commands.xclaim(bytes(STREAM), Consumer.from(bytes(GROUP), bytes("dead-worker")),
                        new XClaimArgs().minIdleTime(Duration.ZERO).idle(Duration.ofSeconds(31)), oldEntry).get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            } catch (java.util.concurrent.ExecutionException exception) {
                throw new IllegalStateException(exception.getCause());
            }
            return null;
        });

        List<MapRecord<String, String, String>> reclaimed = handoff.reclaimPendingEntries();

        assertThat(reclaimed).extracting(record -> record.getId().getValue()).containsExactly(oldEntry);
        assertThat(reclaimed).extracting(record -> record.getId().getValue()).doesNotContain(youngEntry);
    }

    private String add(String marker) {
        return redis.<String, String>opsForStream().add(STREAM, Map.of("marker", marker)).getValue();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
