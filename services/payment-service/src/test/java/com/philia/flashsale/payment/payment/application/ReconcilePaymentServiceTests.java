package com.philia.flashsale.payment.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutExpireRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory;
import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentCommand;
import com.philia.flashsale.payment.payment.application.model.recovery.ReconcilePaymentResult;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.PaymentRecoveryPolicy;
import com.philia.flashsale.payment.payment.application.service.ReconcilePaymentService;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentAttempt;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Application proof for same-key recovery, deadline expiry, late success, and bounded unknowns. */
class ReconcilePaymentServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Test
    void createRecoveryReusesTheOriginalProviderKeyAndSessionWorkflow() {
        Fixture fixture = fixture(HostedCheckoutResult.open("cs_recovered", "https://checkout.test/recovered",
                NOW.plusSeconds(1800), NOW));
        PaymentAttempt attempt = fixture.allocateCreatingAttempt();
        PaymentRecoveryWorkPort.Work work = fixture.work(attempt, "CREATE_SESSION", 1);

        ReconcilePaymentResult result = fixture.service.reconcile(new ReconcilePaymentCommand(work, NOW));

        assertThat(result.outcome()).isEqualTo(ReconcilePaymentResult.Outcome.CONVERGED);
        assertThat(fixture.provider.createRequest.providerIdempotencyKey())
                .isEqualTo(attempt.providerIdempotencyKey());
        assertThat(fixture.payment.activeAttempt().providerSessionId()).isEqualTo("cs_recovered");
        assertThat(fixture.workStore.completed).contains(work.id());
    }

    @Test
    void openSessionAtDeadlineIsExpiredOnlyAfterProviderConfirmsExpiry() {
        Fixture fixture = fixture(new HostedCheckoutResult(ProviderCheckoutState.EXPIRED, "cs_expire",
                null, null, NOW.plusSeconds(2), NOW.plusSeconds(2), null));
        fixture.payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                java.math.BigDecimal.valueOf(100_000), "VND", NOW.plusSeconds(1), NOW.minusSeconds(300));
        PaymentAttempt attempt = fixture.allocateOpenAttempt();
        fixture.now = NOW.plusSeconds(2);
        PaymentRecoveryWorkPort.Work work = fixture.work(attempt, "EXPIRE_SESSION", 1);

        ReconcilePaymentResult result = fixture.service.reconcile(new ReconcilePaymentCommand(work, NOW));

        assertThat(result.outcome()).isEqualTo(ReconcilePaymentResult.Outcome.CONVERGED);
        assertThat(fixture.payment.status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(fixture.outbox.records).hasSize(1);
    }

    @Test
    void verifiedLateSuccessWinsAfterAPreviouslyUnpaidAttempt() {
        Fixture fixture = fixture(new HostedCheckoutResult(ProviderCheckoutState.PAID, "cs_late",
                "pi_late", null, null, NOW, null));
        PaymentAttempt attempt = fixture.allocateOpenAttempt();
        fixture.payment.markProviderTerminalFailure(attempt.id(), NOW.minusSeconds(1));
        PaymentRecoveryWorkPort.Work work = fixture.work(attempt, "REFRESH_SESSION", 1);

        ReconcilePaymentResult result = fixture.service.reconcile(new ReconcilePaymentCommand(work, NOW));

        assertThat(result.outcome()).isEqualTo(ReconcilePaymentResult.Outcome.CONVERGED);
        assertThat(fixture.payment.status()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(fixture.outbox.records).hasSize(1);
    }

    @Test
    void unknownProviderStateIsDeferredWithoutAFinancialFailure() {
        Fixture fixture = fixture(HostedCheckoutResult.unknown(ProviderFailureCategory.TIMEOUT, NOW));
        PaymentAttempt attempt = fixture.allocateOpenAttempt();
        PaymentRecoveryWorkPort.Work work = fixture.work(attempt, "REFRESH_SESSION", 1);

        ReconcilePaymentResult result = fixture.service.reconcile(new ReconcilePaymentCommand(work, NOW));

        assertThat(result.outcome()).isEqualTo(ReconcilePaymentResult.Outcome.DEFERRED);
        assertThat(fixture.payment.status()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(fixture.outbox.records).isEmpty();
        assertThat(fixture.workStore.rescheduled).contains(work.id());
    }

    private Fixture fixture(HostedCheckoutResult providerResult) {
        return new Fixture(providerResult);
    }

    private static final class Fixture {
        Payment payment = Payment.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                java.math.BigDecimal.valueOf(100_000), "VND", NOW.plusSeconds(300), NOW.minusSeconds(1));
        final Store store = new Store();
        final WorkStore workStore = new WorkStore();
        final FakeProvider provider;
        final OutboxStore outbox = new OutboxStore();
        Instant now = NOW;
        final ReconcilePaymentService service;

        Fixture(HostedCheckoutResult result) {
            store.payments.put(payment.id(), payment);
            provider = new FakeProvider(result);
            service = new ReconcilePaymentService(workStore, store, store, provider, () -> now,
                    new ImmediateTransactions(), outbox, new PaymentRecoveryPolicy());
        }

        PaymentAttempt allocateCreatingAttempt() {
            PaymentAttempt attempt = payment.allocateAttempt(UUID.randomUUID(), "payment-checkout-stable",
                    NOW.minusSeconds(1), NOW.plusSeconds(23 * 60 * 60));
            attempt.markSubmitted(NOW.minusSeconds(1));
            store.payments.put(payment.id(), payment);
            return attempt;
        }

        PaymentAttempt allocateOpenAttempt() {
            PaymentAttempt attempt = allocateCreatingAttempt();
            payment.markAttemptOpen(attempt.id(), "cs_expire", NOW.plusSeconds(1800), NOW.minusSeconds(1));
            return payment.activeAttempt();
        }

        PaymentRecoveryWorkPort.Work work(PaymentAttempt attempt, String type, int attemptCount) {
            return new PaymentRecoveryWorkPort.Work(UUID.randomUUID(), payment.id(), attempt.id(), type,
                    "IN_PROGRESS", attempt.providerIdempotencyKey(), attempt.safeReplayUntil(), attemptCount,
                    NOW, "worker", NOW.plusSeconds(30), null, NOW, NOW);
        }
    }

    private static final class Store implements LoadPaymentPort, SavePaymentPort {
        final Map<UUID, Payment> payments = new HashMap<>();
        @Override public Optional<Payment> findById(UUID id) { return Optional.ofNullable(payments.get(id)); }
        @Override public Optional<Payment> findByOrderId(UUID id) { return payments.values().stream()
                .filter(payment -> payment.orderId().equals(id)).findFirst(); }
        @Override public Optional<Payment> findLockedById(UUID id) { return findById(id); }
        @Override public Payment save(Payment value) { payments.put(value.id(), value); return value; }
    }

    private static final class WorkStore implements PaymentRecoveryWorkPort {
        final List<UUID> completed = new ArrayList<>();
        final List<UUID> rescheduled = new ArrayList<>();
        @Override public Work schedule(Work work) { return work; }
        @Override public List<Work> claimWorkBatch(Instant now, int batchSize, String owner, Instant leaseUntil) {
            return List.of();
        }
        @Override public void complete(UUID id, Instant at) { completed.add(id); }
        @Override public void reschedule(UUID id, String reason, Instant nextAt, Instant at) {
            rescheduled.add(id);
        }
        @Override public void markManualReview(UUID id, String reason, Instant at) { }
    }

    private static final class FakeProvider implements HostedCheckoutProviderPort {
        final HostedCheckoutResult result;
        HostedCheckoutCreateRequest createRequest;
        FakeProvider(HostedCheckoutResult result) { this.result = result; }
        @Override public HostedCheckoutResult create(HostedCheckoutCreateRequest request) {
            createRequest = request;
            return result;
        }
        @Override public HostedCheckoutResult retrieve(HostedCheckoutRetrieveRequest request) { return result; }
        @Override public HostedCheckoutResult expire(HostedCheckoutExpireRequest request) { return result; }
    }

    private static final class OutboxStore implements SavePaymentOutboxPort {
        final List<OutboxRecord> records = new ArrayList<>();
        @Override public OutboxRecord save(OutboxRecord record) { records.add(record); return record; }
    }

    private static final class ImmediateTransactions implements com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort {
        @Override public <T> T execute(java.util.function.Supplier<T> work) { return work.get(); }
    }
}
