package com.philia.flashsale.payment.payment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.payment.payment.application.exception.PaymentCheckoutException;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutCommand;
import com.philia.flashsale.payment.payment.application.model.StartCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutExpireRequest;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutResult;
import com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutRetrieveRequest;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderCheckoutState;
import com.philia.flashsale.payment.payment.application.model.provider.ProviderFailureCategory;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClientIdempotencyPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.CheckoutPersistenceService;
import com.philia.flashsale.payment.payment.application.service.StartCheckoutService;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Application proof that the durable allocation precedes provider I/O and replays are safe. */
class StartCheckoutServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");
    private static final UUID OWNER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void newCheckoutCommitsAttemptBeforeProviderAndReturnsCreated() {
        Fixture fixture = new Fixture(HostedCheckoutResult.open("cs_test_1",
                "https://checkout.test/one", NOW.plusSeconds(1800), NOW));

        StartCheckoutResult result = fixture.service.start(new StartCheckoutCommand(fixture.payment.id(), OWNER, "k1"));

        assertThat(result.outcome()).isEqualTo(StartCheckoutResult.Outcome.CREATED);
        assertThat(result.checkoutUrl()).isEqualTo("https://checkout.test/one");
        assertThat(fixture.provider.sawAttemptBeforeCall).isTrue();
        assertThat(fixture.payment.activeAttempt().providerSessionId()).isEqualTo("cs_test_1");
    }

    @Test
    void sameKeyReplaysByPersistedSessionIdentity() {
        Fixture fixture = new Fixture(HostedCheckoutResult.open("cs_test_2",
                "https://checkout.test/two", NOW.plusSeconds(1800), NOW));
        var command = new StartCheckoutCommand(fixture.payment.id(), OWNER, "same-key");
        fixture.service.start(command);
        StartCheckoutResult replay = fixture.service.start(command);

        assertThat(replay.outcome()).isEqualTo(StartCheckoutResult.Outcome.REPLAYED);
        assertThat(fixture.provider.createCalls).isEqualTo(1);
        assertThat(fixture.provider.retrieveCalls).isEqualTo(1);
    }

    @Test
    void differentOwnerAndPaymentCannotReuseClientKey() {
        Fixture fixture = new Fixture(HostedCheckoutResult.open("cs_test_3", "https://checkout.test/three",
                NOW.plusSeconds(1800), NOW));
        fixture.service.start(new StartCheckoutCommand(fixture.payment.id(), OWNER, "key"));

        assertThatThrownBy(() -> fixture.service.start(new StartCheckoutCommand(fixture.payment.id(),
                UUID.fromString("33333333-3333-3333-3333-333333333333"), "key")))
                .isInstanceOf(PaymentCheckoutException.class)
                .extracting("outcome").isEqualTo(PaymentCheckoutException.Outcome.IDEMPOTENCY_CONFLICT);
    }

    @Test
    void ambiguousProviderResultBecomesDurableUnknownAnd202Outcome() {
        Fixture fixture = new Fixture(HostedCheckoutResult.unknown(ProviderFailureCategory.TIMEOUT, NOW));
        StartCheckoutResult result = fixture.service.start(new StartCheckoutCommand(fixture.payment.id(), OWNER, "timeout"));

        assertThat(result.outcome()).isEqualTo(StartCheckoutResult.Outcome.RECOVERING);
        assertThat(result.checkoutUrl()).isNull();
        assertThat(fixture.payment.status()).isEqualTo(com.philia.flashsale.payment.payment.domain.model.PaymentStatus.UNKNOWN);
        assertThat(fixture.recovery.works).hasSize(1);
    }

    private static final class Fixture {
        final UUID paymentId = UUID.randomUUID();
        final Payment payment = Payment.create(paymentId, UUID.randomUUID(), OWNER,
                com.philia.flashsale.payment.payment.domain.model.Money.vnd(100_000), NOW.plusSeconds(300), NOW);
        final Store store = new Store(payment);
        final Idempotency idempotency = new Idempotency();
        final Recovery recovery = new Recovery();
        final FakeProvider provider;
        final StartCheckoutService service;

        Fixture(HostedCheckoutResult result) {
            provider = new FakeProvider(result, payment);
            CheckoutPersistenceService persistence = new CheckoutPersistenceService(store, store, idempotency,
                    recovery, () -> NOW, new SequenceIdentity(), new ImmediateTransactions());
            service = new StartCheckoutService(persistence, provider);
        }
    }

    private static final class Store implements LoadPaymentPort, SavePaymentPort {
        private final Map<UUID, Payment> payments = new HashMap<>();
        Store(Payment payment) { payments.put(payment.id(), payment); }
        @Override public Optional<Payment> findById(UUID id) { return Optional.ofNullable(payments.get(id)); }
        @Override public Optional<Payment> findByOrderId(UUID id) { return payments.values().stream()
                .filter(p -> p.orderId().equals(id)).findFirst(); }
        @Override public Optional<Payment> findLockedById(UUID id) { return findById(id); }
        @Override public Payment save(Payment payment) { payments.put(payment.id(), payment); return payment; }
    }

    private static final class SequenceIdentity implements com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort {
        private int value;
        @Override public UUID newId() { return UUID.nameUUIDFromBytes(("id-" + value++).getBytes()); }
    }

    private static final class ImmediateTransactions implements com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort {
        @Override public <T> T execute(java.util.function.Supplier<T> work) { return work.get(); }
    }

    private static final class Idempotency implements PaymentClientIdempotencyPort {
        final Map<String, Record> records = new HashMap<>();
        @Override public Optional<Record> findLocked(String operation, String keyDigest) {
            return Optional.ofNullable(records.get(operation + ":" + keyDigest));
        }
        @Override public Record create(UUID id, String operation, String keyDigest, UUID userId, UUID paymentId,
                String fingerprint, Instant createdAt) {
            Record record = new Record(id, operation, keyDigest, userId, paymentId, fingerprint, null,
                    "ACCEPTED", createdAt, createdAt);
            records.put(operation + ":" + keyDigest, record);
            return record;
        }
        @Override public Record attachAttempt(UUID id, UUID attemptId, String outcome, Instant updatedAt) {
            for (var entry : records.entrySet()) {
                if (entry.getValue().id().equals(id)) {
                    Record old = entry.getValue();
                    Record updated = new Record(old.id(), old.operation(), old.keyDigest(), old.userId(), old.paymentId(),
                            old.requestFingerprint(), attemptId, outcome, old.createdAt(), updatedAt);
                    entry.setValue(updated);
                    return updated;
                }
            }
            throw new IllegalStateException("missing idempotency record");
        }
    }

    private static final class Recovery implements PaymentRecoveryWorkPort {
        final List<Work> works = new java.util.ArrayList<>();
        @Override public Work schedule(Work work) { works.add(work); return work; }
        @Override public List<Work> claimWorkBatch(Instant now, int batchSize, String owner, Instant leaseUntil) { return List.of(); }
        @Override public void complete(UUID workId, Instant completedAt) { }
        @Override public void markManualReview(UUID workId, String errorCode, Instant observedAt) { }
    }

    private static final class FakeProvider implements HostedCheckoutProviderPort {
        final HostedCheckoutResult result;
        final Payment payment;
        int createCalls;
        int retrieveCalls;
        boolean sawAttemptBeforeCall;
        FakeProvider(HostedCheckoutResult result, Payment payment) { this.result = result; this.payment = payment; }
        @Override public HostedCheckoutResult create(com.philia.flashsale.payment.payment.application.model.provider.HostedCheckoutCreateRequest request) {
            createCalls++; sawAttemptBeforeCall = payment.activeAttempt() != null; return result;
        }
        @Override public HostedCheckoutResult retrieve(HostedCheckoutRetrieveRequest request) { retrieveCalls++; return result; }
        @Override public HostedCheckoutResult expire(HostedCheckoutExpireRequest request) { return result; }
    }
}
