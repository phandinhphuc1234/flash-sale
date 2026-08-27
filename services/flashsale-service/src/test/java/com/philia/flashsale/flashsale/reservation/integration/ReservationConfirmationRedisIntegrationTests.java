package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationConfirmationRedisAdapter;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationRedisKeys;
import com.philia.flashsale.flashsale.reservation.application.command.ConfirmReservationCommand;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationConfirmationResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReservationConfirmationRedisIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void confirmationRemovesExpiryWithoutRestoringQuotaAndReplayIsIdempotent() {
        UUID campaignId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String reservationKey = ReservationRedisKeys.reservation(campaignId, reservationId);
        redis.opsForHash().put(reservationKey, "status", "RESERVED");
        redis.opsForHash().put(ReservationRedisKeys.stock(campaignId), variantId.toString(), "0");
        redis.opsForHash().put(ReservationRedisKeys.userQuantity(campaignId, userId), variantId.toString(), "1");
        redis.opsForZSet().add(ReservationRedisKeys.expirations(), reservationId.toString(),
                NOW.plusSeconds(300).toEpochMilli());
        ConfirmReservationCommand command = command(reservationId);
        ReservationConfirmationResult result = new ReservationConfirmationResult(reservationId, campaignId,
                command.commandId(), UUID.randomUUID(), ReservationConfirmationResult.Status.CONFIRMED, NOW);
        var adapter = new ReservationConfirmationRedisAdapter(redis);

        adapter.confirm(command, result);
        adapter.confirm(command, result);

        assertThat(redis.opsForHash().get(reservationKey, "status")).isEqualTo("CONFIRMED");
        assertThat(redis.opsForHash().get(ReservationRedisKeys.stock(campaignId), variantId.toString()))
                .isEqualTo("0");
        assertThat(redis.opsForHash().get(ReservationRedisKeys.userQuantity(campaignId, userId),
                variantId.toString())).isEqualTo("1");
        assertThat(redis.opsForZSet().score(ReservationRedisKeys.expirations(), reservationId.toString())).isNull();
    }

    private ConfirmReservationCommand command(UUID reservationId) {
        UUID sagaId = UUID.randomUUID();
        return new ConfirmReservationCommand(UUID.randomUUID(), "ConfirmPurchaseReservation", 1,
                "order-service", "PURCHASE_SAGA", sagaId, 1, sagaId, UUID.randomUUID(), NOW,
                UUID.randomUUID(), sagaId, reservationId, UUID.randomUUID(), NOW,
                "flashsale.purchase.commands.v1", 0, 1, null, null,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    }
}
