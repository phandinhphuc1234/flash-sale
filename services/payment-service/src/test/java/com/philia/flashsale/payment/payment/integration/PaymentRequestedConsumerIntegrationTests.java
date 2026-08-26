package com.philia.flashsale.payment.payment.integration;

import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.COMMAND_TOPIC;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.TRACEPARENT;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.await;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.bootstrapServers;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.consume;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.event;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.header;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.producer;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.schemaRegistryUrl;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.send;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa.repository.PaymentOutboxEventJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentCommandInboxJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.CannotCreateTransactionException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Live G4 proof for real broker/Registry delivery, deduplication, retry, and commit replay. */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "payment.kafka.integration", matches = "true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "payment.acceptance.enabled=true",
        "payment.checkout.enabled=false",
        "payment.stripe.enabled=false",
        "payment.recovery.enabled=false",
        "payment.kafka.consumer-enabled=true",
        "payment.kafka.outbox-publisher-enabled=false",
        "payment.kafka.retry-delays=1s,3s,10s",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.io.confluent.kafka=WARN",
        "logging.level.org.springframework.kafka=WARN",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PaymentRequestedConsumerIntegrationTests {

    private static final String GROUP = "payment-g4-consumer-" + UUID.randomUUID();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db").withUsername("flashsale").withPassword("test-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", PaymentRequestedKafkaLiveTestSupport::bootstrapServers);
        registry.add("spring.kafka.properties[schema.registry.url]",
                PaymentRequestedKafkaLiveTestSupport::schemaRegistryUrl);
        registry.add("spring.kafka.consumer.properties[schema.registry.url]",
                PaymentRequestedKafkaLiveTestSupport::schemaRegistryUrl);
        registry.add("spring.kafka.producer.properties[schema.registry.url]",
                PaymentRequestedKafkaLiveTestSupport::schemaRegistryUrl);
        registry.add("payment.kafka.consumer-group", () -> GROUP);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired PaymentJpaRepository payments;
    @Autowired PaymentCommandInboxJpaRepository inbox;
    @Autowired PaymentOutboxEventJpaRepository outbox;
    @Autowired KafkaListenerEndpointRegistry listeners;

    @MockitoSpyBean
    AcceptPaymentRequestUseCase acceptance;

    @BeforeEach
    void resetState() throws Exception {
        await("Kafka listener assignment", Duration.ofSeconds(30), () -> listeners.getListenerContainers()
                .stream().allMatch(container -> !container.getAssignedPartitions().isEmpty()));
        jdbc.update("truncate payment_outbox_events, payment_recovery_work, "
                + "payment_provider_event_receipts, payment_client_idempotency, "
                + "payment_command_inbox, payment_attempts, payments cascade");
        clearInvocations(acceptance);
    }

    @Test
    void oneHundredPhysicalDuplicatesConvergeAfterCommitWithStableKeyAndTraceHeaders() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentRequestedV1 command = event(eventId, orderId, UUID.randomUUID());
        List<RecordMetadata> metadata = new ArrayList<>();

        try (var kafka = producer()) {
            for (int index = 0; index < 100; index++) {
                metadata.add(send(kafka, orderId.toString(), command));
            }
        }

        verify(acceptance, timeout(45_000).times(100)).accept(any());
        assertThat(metadata).extracting(RecordMetadata::topic).containsOnly(COMMAND_TOPIC);
        assertThat(metadata).extracting(RecordMetadata::partition).containsOnly(metadata.getFirst().partition());
        var observed = consume(COMMAND_TOPIC, record -> record.value() instanceof PaymentRequestedV1 value
                && eventId.equals(value.getEventId()));
        assertThat(observed.key()).isEqualTo(orderId.toString());
        assertThat(header(observed, "traceparent")).isEqualTo(TRACEPARENT);
        assertThat(header(observed, "tracestate")).isEqualTo("vendor=value");
        assertThat(payments.count()).isOne();
        assertThat(inbox.count()).isOne();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void transientFailureUsesOneThreeTenSecondBackoffThenCommitsBeforeAcknowledgement() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        AtomicInteger calls = new AtomicInteger();
        List<Long> callTimes = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            callTimes.add(System.nanoTime());
            if (calls.incrementAndGet() <= 3) {
                throw new CannotCreateTransactionException("transient test database outage");
            }
            return invocation.callRealMethod();
        }).when(acceptance).accept(any());

        try (var kafka = producer()) {
            send(kafka, orderId.toString(), event(eventId, orderId, UUID.randomUUID()));
        }

        await("PaymentRequested retry convergence", Duration.ofSeconds(30), () -> payments.count() == 1);
        verify(acceptance, timeout(1_000).times(4)).accept(any());
        assertThat(callTimes).hasSize(4);
        assertThat(elapsedMillis(callTimes, 0, 1)).isGreaterThanOrEqualTo(900);
        assertThat(elapsedMillis(callTimes, 1, 2)).isGreaterThanOrEqualTo(2_900);
        assertThat(elapsedMillis(callTimes, 2, 3)).isGreaterThanOrEqualTo(9_900);
        assertThat(inbox.count()).isOne();
        assertThat(outbox.count()).isZero();
    }

    private long elapsedMillis(List<Long> times, int start, int end) {
        return Duration.ofNanos(times.get(end) - times.get(start)).toMillis();
    }
}
