package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationRedisKeys;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationReleaseRedisAdapter;
import com.philia.flashsale.flashsale.reservation.application.command.ReleaseReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationReleaseResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Proves the Redis release Lua script restores stock/quota at most once. */
@Testcontainers
class ReservationReleaseRedisIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private UUID campaignId;
    private UUID reservationId;
    private UUID variantId;
    private UUID userId;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        try (var connection = connectionFactory.getConnection()) { connection.serverCommands().flushAll(); }
        campaignId = UUID.randomUUID(); reservationId = UUID.randomUUID(); variantId = UUID.randomUUID(); userId = UUID.randomUUID();
        redis.opsForHash().put(ReservationRedisKeys.reservation(campaignId, reservationId), "status", "RELEASED");
        redis.opsForHash().put(ReservationRedisKeys.stock(campaignId), variantId.toString(), "0");
        redis.opsForHash().put(ReservationRedisKeys.userQuantity(campaignId, userId), variantId.toString(), "1");
        redis.opsForZSet().add(ReservationRedisKeys.expirations(), reservationId.toString(), NOW.toEpochMilli());
    }

    @AfterEach
    void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void replayDoesNotRestoreStockOrQuotaTwice() {
        var adapter = new ReservationReleaseRedisAdapter(redis);
        var command = command();
        var result = new ReservationReleaseResult(reservationId, campaignId, userId, variantId, 1,
                command.commandId(), UUID.randomUUID(), ReservationReleaseResult.Status.RELEASED,
                command.reason(), NOW);

        adapter.release(command, result);
        adapter.release(command, result);

        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString())).isEqualTo("1");
        assertThat(redis.opsForHash().hasKey(ReservationRedisKeys.userQuantity(campaignId, userId), variantId.toString())).isFalse();
        assertThat(redis.opsForHash().get(ReservationRedisKeys.reservation(campaignId, reservationId), "quotaReleased")).isEqualTo("true");
        assertThat(redis.opsForZSet().score(ReservationRedisKeys.expirations(), reservationId.toString())).isNull();
    }

    private ReleaseReservationCommand command() {
        UUID sagaId = UUID.randomUUID();
        return new ReleaseReservationCommand(UUID.randomUUID(), "ReleasePurchaseReservation", 1,
                "order-service", "PURCHASE_SAGA", sagaId, 1, sagaId, UUID.randomUUID(), NOW,
                UUID.randomUUID(), UUID.randomUUID(), reservationId, "PROVIDER_TERMINAL_FAILURE",
                "flashsale.purchase.commands.v1", 0, 1, null, null,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }
}
