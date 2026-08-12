package com.philia.flashsale.flashsale.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.flashsale.configuration.RedisHotPathProperties;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis.ReservationHandoffConsumerGroupInitializer;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis.ReservationHandoffMessageMapper;
import com.philia.flashsale.flashsale.reservation.adapter.in.messaging.redis.ReservationHandoffStreamConsumer;
import com.philia.flashsale.flashsale.reservation.adapter.out.redis.ReservationHandoffRedisAdapter;
import com.philia.flashsale.flashsale.reservation.application.port.out.PersistAcceptedPurchasePort;
import com.philia.flashsale.flashsale.reservation.application.usecase.ReservationAcceptanceFlow;
import com.philia.flashsale.flashsale.reservation.domain.model.AcceptedReservationSnapshot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ReservationHandoffRecoveryIntegrationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T12:00:00Z");
    private static final String STREAM = "fs:{hot}:handoff";
    private static final String GROUP = "flashsale-durable-acceptance-v1";

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redis;
    private RedisHotPathProperties properties;
    private ReservationHandoffRedisAdapter handoff;
    private ReservationHandoffConsumerGroupInitializer initializer;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        try (var connection = connectionFactory.getConnection()) {
            connection.serverCommands().flushAll();
        }
        properties = new RedisHotPathProperties(STREAM, GROUP, Duration.ofMillis(1), 100, Duration.ZERO);
        handoff = new ReservationHandoffRedisAdapter(redis, properties, "worker-a");
        initializer = new ReservationHandoffConsumerGroupInitializer(redis, properties);
    }

    @AfterEach
    void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void persistsTheSameImmutableWinnerAndDeletesTheEntryAfterTerminalAcceptance() {
        AcceptedReservationSnapshot expected = snapshot();
        redis.<String, String>opsForStream().add(STREAM, fields(expected));
        AtomicReference<AcceptedReservationSnapshot> persisted = new AtomicReference<>();
        PersistAcceptedPurchasePort persistence = persisted::set;
        ReservationAcceptanceFlow flow = new ReservationAcceptanceFlow(persistence,
                (reservationId, entryId) -> handoff.acknowledge(reservationId, entryId));
        ReservationHandoffStreamConsumer consumer = consumer(flow, persistence);

        consumer.pollOnce();

        assertThat(persisted.get()).isEqualTo(expected);
        assertThat(redis.opsForStream().size(STREAM)).isZero();
    }

    @Test
    void leavesDatabaseFailuresPendingAndReclaimsThemWithXautoclaim() {
        AcceptedReservationSnapshot expected = snapshot();
        redis.<String, String>opsForStream().add(STREAM, fields(expected));
        AtomicBoolean unavailable = new AtomicBoolean(true);
        AtomicReference<AcceptedReservationSnapshot> persisted = new AtomicReference<>();
        PersistAcceptedPurchasePort persistence = value -> {
            if (unavailable.get()) {
                throw new IllegalStateException("postgres unavailable");
            }
            persisted.set(value);
        };
        ReservationAcceptanceFlow flow = new ReservationAcceptanceFlow(persistence,
                (reservationId, entryId) -> handoff.acknowledge(reservationId, entryId));
        ReservationHandoffStreamConsumer consumer = consumer(flow, persistence);

        consumer.pollOnce();
        assertThat(redis.opsForStream().size(STREAM)).isEqualTo(1);

        unavailable.set(false);
        consumer.pollOnce();

        assertThat(persisted.get()).isEqualTo(expected);
        assertThat(redis.opsForStream().size(STREAM)).isZero();
    }

    @Test
    void replaysAfterDatabaseCommitBeforeAckWithoutChangingStableIdentity() {
        AcceptedReservationSnapshot expected = snapshot();
        String entryId = redis.<String, String>opsForStream().add(STREAM, fields(expected)).getValue();
        AtomicInteger persists = new AtomicInteger();
        AtomicBoolean failAck = new AtomicBoolean(true);
        PersistAcceptedPurchasePort persistence = value -> persists.incrementAndGet();
        ReservationAcceptanceFlow flow = new ReservationAcceptanceFlow(persistence, (reservationId, id) -> {
            if (failAck.getAndSet(false)) {
                throw new IllegalStateException("ack lost");
            }
            handoff.acknowledge(reservationId, id);
        });
        ReservationHandoffStreamConsumer consumer = consumer(flow, persistence);

        consumer.pollOnce();
        assertThat(redis.opsForStream().size(STREAM)).isEqualTo(1);

        consumer.pollOnce();

        assertThat(persists).hasValue(2);
        assertThat(redis.opsForStream().size(STREAM)).isZero();
        assertThat(entryId).isNotBlank();
    }

    private ReservationHandoffStreamConsumer consumer(ReservationAcceptanceFlow flow,
            PersistAcceptedPurchasePort persistence) {
        return new ReservationHandoffStreamConsumer(initializer, handoff, new ReservationHandoffMessageMapper(),
                flow, persistence, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    static Map<String, String> fields(AcceptedReservationSnapshot snapshot) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("purchaseRequestId", snapshot.purchaseRequestId().toString());
        fields.put("reservationId", snapshot.reservationId().toString());
        fields.put("eventId", snapshot.eventId().toString());
        fields.put("campaignId", snapshot.campaignId().toString());
        fields.put("variantId", snapshot.variantId().toString());
        fields.put("userId", snapshot.userId().toString());
        fields.put("inventoryAllocationId", snapshot.inventoryAllocationId().toString());
        fields.put("skuSnapshot", snapshot.skuSnapshot());
        fields.put("unitPrice", snapshot.unitPrice().toPlainString());
        fields.put("currency", snapshot.currency());
        fields.put("quantity", Long.toString(snapshot.quantity()));
        fields.put("requestHash", snapshot.requestHash());
        fields.put("idempotencyKeyHash", snapshot.idempotencyKeyHash());
        fields.put("acceptedAt", Long.toString(snapshot.acceptedAt().toEpochMilli()));
        fields.put("expiresAt", Long.toString(snapshot.expiresAt().toEpochMilli()));
        fields.put("retainedUntil", Long.toString(snapshot.retainedUntil().toEpochMilli()));
        fields.put("traceparent", snapshot.traceparent());
        fields.put("tracestate", snapshot.tracestate());
        return fields;
    }

    private AcceptedReservationSnapshot snapshot() {
        String hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        return new AcceptedReservationSnapshot(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-1",
                new BigDecimal("19.9900"), "VND", 1, hash, hash, NOW, NOW.plusSeconds(300),
                NOW.plusSeconds(3600), "00-trace", "");
    }
}
