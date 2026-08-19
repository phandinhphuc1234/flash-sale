package com.philia.flashsale.payment.payment.integration;

import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.DLT_TOPIC;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.await;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.consume;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.event;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.header;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.producer;
import static com.philia.flashsale.payment.payment.integration.PaymentRequestedKafkaLiveTestSupport.send;
import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.contract.payment.command.v1.PaymentRequestedV1;
import com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa.repository.PaymentOutboxEventJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentCommandInboxJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Live proof that poison commands reach the command DLT and can be corrected and replayed. */
@Testcontainers(disabledWithoutDocker = true)
@EnabledIfSystemProperty(named = "payment.kafka.integration", matches = "true")
@SpringBootTest(properties = {
        "payment.acceptance.enabled=true",
        "payment.checkout.enabled=false",
        "payment.stripe.enabled=false",
        "payment.recovery.enabled=false",
        "payment.kafka.consumer-enabled=true",
        "payment.kafka.outbox-publisher-enabled=false",
        "logging.level.org.apache.kafka=WARN",
        "logging.level.io.confluent.kafka=WARN",
        "logging.level.org.springframework.kafka=WARN",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PaymentRequestedDltIntegrationTests {

    private static final String GROUP = "payment-g4-dlt-" + UUID.randomUUID();

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

    @BeforeEach
    void resetState() throws Exception {
        await("Kafka listener assignment", Duration.ofSeconds(30), () -> listeners.getListenerContainers()
                .stream().allMatch(container -> !container.getAssignedPartitions().isEmpty()));
        jdbc.update("truncate payment_outbox_events, payment_recovery_work, "
                + "payment_provider_event_receipts, payment_client_idempotency, "
                + "payment_command_inbox, payment_attempts, payments cascade");
    }

    @Test
    void poisonKeyIsDeadLetteredWithSafeDiagnosticsAndCorrectedReplayConverges() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        PaymentRequestedV1 command = event(eventId, orderId, UUID.randomUUID());

        try (var kafka = producer()) {
            send(kafka, UUID.randomUUID().toString(), command);
        }

        var deadLetter = consume(DLT_TOPIC, record -> record.value() instanceof PaymentRequestedV1 value
                && eventId.equals(value.getEventId()));
        assertThat(deadLetter.value()).isInstanceOf(PaymentRequestedV1.class);
        assertThat(header(deadLetter, KafkaHeaders.DLT_ORIGINAL_TOPIC))
                .isEqualTo(PaymentRequestedKafkaLiveTestSupport.COMMAND_TOPIC);
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_FQCN))
                .endsWith("ListenerExecutionFailedException");
        assertThat(header(deadLetter, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN))
                .endsWith("PaymentRequestedRecordException");
        String diagnostic = header(deadLetter, KafkaHeaders.DLT_EXCEPTION_MESSAGE);
        assertThat(diagnostic).isNotBlank().doesNotContain("sk_", "whsec_", "card_number");
        assertThat(payments.count()).isZero();
        assertThat(inbox.count()).isZero();

        try (var kafka = producer()) {
            send(kafka, orderId.toString(), command);
        }
        await("operator replay convergence", Duration.ofSeconds(20), () -> payments.count() == 1);
        assertThat(inbox.count()).isOne();
        assertThat(outbox.count()).isZero();
    }
}
