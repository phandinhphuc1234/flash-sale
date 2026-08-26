package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentAttemptJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentClientIdempotencyJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentCommandInboxJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentProviderEventReceiptJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentRecoveryWorkJpaRepository;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult;
import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentCommandInboxPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.AcceptPaymentRequestService;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import com.philia.flashsale.payment.outbox.adapter.out.persistence.jpa.repository.PaymentOutboxEventJpaRepository;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof for atomic PaymentRequested acceptance and semantic replay/convergence. */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "payment.kafka.consumer-enabled=false",
        "payment.kafka.outbox-publisher-enabled=false",
        "payment.acceptance.enabled=true",
        "payment.recovery.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class PaymentCommandAcceptanceIntegrationTests {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("flashsale")
            .withPassword("test-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private AcceptPaymentRequestUseCase acceptance;

    @Autowired
    private PaymentJpaRepository payments;

    @Autowired
    private PaymentCommandInboxJpaRepository inbox;

    @Autowired
    private PaymentOutboxEventJpaRepository outbox;

    @Autowired
    private PaymentAttemptJpaRepository attempts;

    @Autowired
    private PaymentClientIdempotencyJpaRepository clientIdempotency;

    @Autowired
    private PaymentProviderEventReceiptJpaRepository providerReceipts;

    @Autowired
    private PaymentRecoveryWorkJpaRepository recoveryWork;

    @Autowired
    private PaymentCommandInboxPort inboxPort;

    @Autowired
    private LoadPaymentPort loadPaymentPort;

    @Autowired
    private SavePaymentPort savePaymentPort;

    @Autowired
    private PaymentClockPort clockPort;

    @Autowired
    private PaymentIdentityPort identityPort;

    @Autowired
    private PaymentTransactionPort transactionPort;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("truncate payment_outbox_events, payment_recovery_work, payment_provider_event_receipts, "
                + "payment_client_idempotency, payment_command_inbox, payment_attempts, payments cascade");
    }

    @Test
    void newCommandCommitsPaymentAndInboxAtomically() {
        AcceptPaymentRequestResult result = acceptance.accept(command(UUID.randomUUID(), NOW.plusSeconds(600),
                new BigDecimal("125.0000")));

        assertThat(result.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.ACCEPTED);
        assertThat(payments.count()).isEqualTo(1);
        assertThat(inbox.count()).isEqualTo(1);
        assertThat(outbox.count()).isZero();
        assertThat(payments.findById(result.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.PENDING);
        assertThat(inbox.findById(result.paymentId()).isPresent()).isFalse();
        assertThat(inbox.findByOrderId(ORDER_ID).orElseThrow().getPayment().getId())
                .isEqualTo(result.paymentId());
    }

    @Test
    void differentEventIdWithEquivalentSnapshotReplaysTheEstablishedPayment() {
        AcceptPaymentRequestResult first = acceptance.accept(command(UUID.randomUUID(), NOW.plusSeconds(600),
                new BigDecimal("125.0000")));
        AcceptPaymentRequestResult replay = acceptance.accept(command(UUID.randomUUID(), NOW.plusSeconds(600),
                new BigDecimal("125.00")));

        assertThat(replay.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.BUSINESS_REPLAYED);
        assertThat(replay.paymentId()).isEqualTo(first.paymentId());
        assertThat(payments.count()).isEqualTo(1);
        assertThat(inbox.count()).isEqualTo(1);
    }

    @Test
    void contradictorySnapshotIsVisibleWithoutMutatingEstablishedPayment() {
        AcceptPaymentRequestResult first = acceptance.accept(command(UUID.randomUUID(), NOW.plusSeconds(600),
                new BigDecimal("125")));
        AcceptPaymentRequestResult conflict = acceptance.accept(command(UUID.randomUUID(), NOW.plusSeconds(600),
                new BigDecimal("250")));

        assertThat(conflict.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.CONFLICT);
        assertThat(payments.findById(first.paymentId()).orElseThrow().getAmount())
                .isEqualByComparingTo("125");
        assertThat(inbox.findByOrderId(ORDER_ID).orElseThrow().getProcessingStatus())
                .isEqualTo("CONFLICTED");
    }

    @Test
    void expiredCommandCreatesTerminalPaymentAndOneStableFailureOutbox() {
        UUID eventId = UUID.randomUUID();
        AcceptPaymentRequestResult first = acceptance.accept(command(eventId, NOW.minusSeconds(1),
                new BigDecimal("125")));
        AcceptPaymentRequestResult replay = acceptance.accept(command(eventId, NOW.minusSeconds(1),
                new BigDecimal("125.0000")));

        assertThat(first.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.EXPIRED);
        assertThat(replay.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.EVENT_REPLAYED);
        assertThat(payments.findById(first.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.EXPIRED);
        assertThat(outbox.count()).isEqualTo(1);
        assertThat(outbox.findAll().get(0).getEventId()).isEqualTo(first.outboxEventId());
    }

    @Test
    void failureBeforeCommitRollsBackPaymentInboxAndOutboxTogether() {
        SavePaymentOutboxPort failingOutbox = record -> {
            throw new IllegalStateException("simulated outbox failure");
        };
        AcceptPaymentRequestUseCase failingAcceptance = new AcceptPaymentRequestService(inboxPort,
                loadPaymentPort, savePaymentPort, failingOutbox, clockPort, identityPort, transactionPort);

        assertThatThrownBy(() -> failingAcceptance.accept(command(UUID.randomUUID(), NOW.minusSeconds(1),
                new BigDecimal("125"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated outbox failure");

        assertThat(payments.count()).isZero();
        assertThat(inbox.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    private AcceptPaymentRequestCommand command(UUID eventId, Instant deadline, BigDecimal amount) {
        return new AcceptPaymentRequestCommand(eventId, "PaymentRequested", 1, "order-service", "ORDER",
                ORDER_ID, 1, UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"), NOW, ORDER_ID, USER_ID, amount,
                "VND", deadline, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null);
    }

    /** Keeps optional G3 application wiring discoverable in slice/test contexts. */
    @TestConfiguration(proxyBeanMethods = false)
    static class PaymentAcceptanceTestConfiguration {

        @Bean
        @Primary
        PaymentClockPort fixedPaymentClock() {
            return () -> NOW;
        }
    }
}
