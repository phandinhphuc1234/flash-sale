package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.payment.payment.application.port.out.PaymentProviderReceiptPort;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL proof that webhook acknowledgement is backed by a safe, reclaimable receipt. */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "payment.acceptance.enabled=true",
        "payment.checkout.enabled=false",
        "payment.stripe.enabled=false",
        "payment.kafka.consumer-enabled=false",
        "payment.kafka.outbox-publisher-enabled=false",
        "payment.webhook.processing.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class StripeWebhookReceiptIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-08-18T08:00:00Z");

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db").withUsername("flashsale").withPassword("test-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired PaymentProviderReceiptPort receipts;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clean() {
        jdbc.update("truncate payment_outbox_events, payment_recovery_work, payment_provider_event_receipts, "
                + "payment_client_idempotency, payment_command_inbox, payment_attempts, payments cascade");
    }

    @Test
    void duplicateEventIdIsOneDurableReceiptAndRawBodyHasNoStorageColumn() {
        UUID firstId = UUID.randomUUID();
        var first = receipts.receive(receipt(firstId, "evt_same"));
        var duplicate = receipts.receive(receipt(UUID.randomUUID(), "evt_same"));

        assertThat(duplicate.id()).isEqualTo(first.id());
        assertThat(jdbc.queryForObject("select count(*) from payment_provider_event_receipts",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select processing_status from payment_provider_event_receipts",
                String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                + "where table_name='payment_provider_event_receipts' and column_name in "
                + "('raw_body','signature','checkout_url')", Integer.class)).isZero();
    }

    @Test
    void claimedReceiptIsReclaimableAfterLeaseAndCanBeCompleted() {
        var received = receipts.receive(receipt(UUID.randomUUID(), "evt_lease"));
        var firstClaim = receipts.claimReceiptBatch(NOW, 10, "worker-a", NOW.plusSeconds(30));
        assertThat(firstClaim).extracting(PaymentProviderReceiptPort.Receipt::id).contains(received.id());

        var reclaimed = receipts.claimReceiptBatch(NOW.plusSeconds(31), 10, "worker-b",
                NOW.plusSeconds(61));
        assertThat(reclaimed).extracting(PaymentProviderReceiptPort.Receipt::id).contains(received.id());
        receipts.markProcessed(received.id(), NOW.plusSeconds(32));

        assertThat(receipts.findByProviderEventId("evt_lease").orElseThrow().processingStatus())
                .isEqualTo("PROCESSED");
    }

    @Test
    void receiptWriteRollsBackWithOwningTransaction() {
        String eventId = "evt_rollback";

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            receipts.receive(receipt(UUID.randomUUID(), eventId));
            throw new IllegalStateException("simulate processing failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(receipts.findByProviderEventId(eventId)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from payment_provider_event_receipts",
                Integer.class)).isZero();
    }

    private PaymentProviderReceiptPort.Receipt receipt(UUID id, String eventId) {
        return new PaymentProviderReceiptPort.Receipt(id, eventId, "checkout.session.completed", false,
                "2026-07-29.dahlia", "cs_test_" + eventId, null, null, null, NOW, NOW,
                "PENDING", null, 0, NOW);
    }
}
