package com.philia.flashsale.payment.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutExpireRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import com.philia.flashsale.payment.payment.application.model.webhook.ProviderEventProcessingResult;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentProviderReceiptPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.ProcessProviderEventService;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Application-level convergence proof independent of Kafka and browser redirects. */
class StripeProviderOutcomeIntegrationTests {
    private static final Instant NOW = Instant.parse("2026-08-18T08:00:00Z");

    @Test
    void outOfOrderAndDuplicateReceiptsProduceOneSuccessFact() {
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("100000"), "VND", NOW.plusSeconds(300), NOW);
        PaymentAttempt attempt = payment.allocateAttempt(UUID.randomUUID(), "provider-key", NOW,
                NOW.plusSeconds(3600));
        payment.markAttemptOpen(attempt.id(), "cs_test", NOW.plusSeconds(1800), NOW);

        InMemoryReceiptPort receipts = new InMemoryReceiptPort();
        receipts.add(receipt(payment, attempt, "evt_expired"));
        receipts.add(receipt(payment, attempt, "evt_paid"));
        receipts.add(receipt(payment, attempt, "evt_paid_duplicate"));
        InMemoryProvider provider = new InMemoryProvider();
        provider.results.put("cs_test", new HostedCheckoutResult(ProviderCheckoutState.PAID,
                "cs_test", "pi_test", null, null, NOW.plusSeconds(2), null));
        InMemoryOutbox outbox = new InMemoryOutbox();
        ProcessProviderEventService service = new ProcessProviderEventService(receipts,
                new InMemoryPayments(payment), new InMemoryPayments(payment), outbox, provider,
                () -> NOW, new PaymentTransactionPort() {
                    @Override public <T> T execute(Supplier<T> work) { return work.get(); }
                }, 100, Duration.ofSeconds(30), 3, Duration.ofSeconds(1));

        ProviderEventProcessingResult result = service.processBatch();

        assertThat(result.claimed()).isEqualTo(3);
        assertThat(result.processed()).isEqualTo(3);
        assertThat(payment.status()).isEqualTo(com.philia.flashsale.payment.payment.domain.model.PaymentStatus.SUCCEEDED);
        assertThat(outbox.records).hasSize(1);
    }

    private PaymentProviderReceiptPort.Receipt receipt(Payment payment, PaymentAttempt attempt, String eventId) {
        return new PaymentProviderReceiptPort.Receipt(UUID.randomUUID(), eventId,
                "checkout.session.completed", false, "2026-07-29.dahlia", "cs_test", payment.id(),
                attempt.id(), payment.orderId(), NOW, NOW, "PENDING", null, 0, NOW);
    }

    private static final class InMemoryPayments implements LoadPaymentPort, SavePaymentPort {
        private final Payment payment;

        private InMemoryPayments(Payment payment) {
            this.payment = payment;
        }

        @Override public java.util.Optional<Payment> findById(UUID id) { return id.equals(payment.id())
                ? java.util.Optional.of(payment) : java.util.Optional.empty(); }
        @Override public java.util.Optional<Payment> findByOrderId(UUID id) { return id.equals(payment.orderId())
                ? java.util.Optional.of(payment) : java.util.Optional.empty(); }
        @Override public java.util.Optional<Payment> findLockedById(UUID id) { return findById(id); }
        @Override public Payment save(Payment value) { return value; }
    }

    private static final class InMemoryReceiptPort implements PaymentProviderReceiptPort {
        private final List<Receipt> records = new ArrayList<>();
        private final Map<UUID, String> statuses = new HashMap<>();
        void add(Receipt receipt) { records.add(receipt); statuses.put(receipt.id(), "PENDING"); }
        @Override public java.util.Optional<Receipt> findByProviderEventId(String id) {
            return records.stream().filter(value -> value.providerEventId().equals(id)).findFirst();
        }
        @Override public Receipt receive(Receipt receipt) { add(receipt); return receipt; }
        @Override public List<Receipt> claimReceiptBatch(Instant now, int size, String owner, Instant until) {
            return records.stream().filter(value -> "PENDING".equals(statuses.get(value.id())))
                    .limit(size).peek(value -> statuses.put(value.id(), "IN_PROGRESS")).toList();
        }
        @Override public void markProcessed(UUID id, Instant at) { statuses.put(id, "PROCESSED"); }
    }

    private static final class InMemoryProvider implements HostedCheckoutProviderPort {
        final Map<String, HostedCheckoutResult> results = new HashMap<>();
        @Override public HostedCheckoutResult retrieve(HostedCheckoutRetrieveRequest request) {
            return results.get(request.providerSessionId());
        }
        @Override public HostedCheckoutResult create(HostedCheckoutCreateRequest request) { return results.values().iterator().next(); }
        @Override public HostedCheckoutResult expire(HostedCheckoutExpireRequest request) { return results.get(request.providerSessionId()); }
    }

    private static final class InMemoryOutbox implements SavePaymentOutboxPort {
        final List<OutboxRecord> records = new ArrayList<>();
        @Override public OutboxRecord save(OutboxRecord record) { records.add(record); return record; }
    }
}
