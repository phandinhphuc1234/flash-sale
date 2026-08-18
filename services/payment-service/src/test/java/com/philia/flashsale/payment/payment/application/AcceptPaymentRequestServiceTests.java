package com.philia.flashsale.payment.payment.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestCommand;
import com.philia.flashsale.payment.payment.application.model.AcceptPaymentRequestResult;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentCommandInboxPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.AcceptPaymentRequestService;
import com.philia.flashsale.payment.payment.domain.model.Payment;
import com.philia.flashsale.payment.payment.domain.model.PaymentStatus;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Application proof for atomic PaymentRequested semantics without Spring or provider calls. */
class AcceptPaymentRequestServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");
    private static final UUID ORDER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void acceptsNewCommandAndPreservesImmutableOrderSnapshot() {
        Fixture fixture = new Fixture();

        AcceptPaymentRequestResult result = fixture.service.accept(command(UUID.randomUUID(),
                NOW.plusSeconds(600), new BigDecimal("125.0000"), "VND"));

        assertThat(result.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.ACCEPTED);
        Payment payment = fixture.payments.values().iterator().next();
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.orderId()).isEqualTo(ORDER_ID);
        assertThat(payment.userId()).isEqualTo(USER_ID);
        assertThat(payment.amount()).isEqualByComparingTo("125");
        assertThat(payment.currency()).isEqualTo("VND");
        assertThat(payment.paymentDeadline()).isEqualTo(NOW.plusSeconds(600));
        assertThat(fixture.inbox.byEvent).hasSize(1);
        assertThat(fixture.inbox.byEvent.values().iterator().next().processingStatus())
                .isEqualTo("PROCESSED");
        assertThat(fixture.outbox.records).isEmpty();
    }

    @Test
    void sameEventReplayReturnsTheEstablishedPaymentWithoutMutation() {
        Fixture fixture = new Fixture();
        UUID eventId = UUID.randomUUID();
        AcceptPaymentRequestCommand command = command(eventId, NOW.plusSeconds(600), new BigDecimal("125"), "VND");

        AcceptPaymentRequestResult first = fixture.service.accept(command);
        AcceptPaymentRequestResult replay = fixture.service.accept(command);

        assertThat(first.paymentId()).isEqualTo(replay.paymentId());
        assertThat(replay.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.EVENT_REPLAYED);
        assertThat(fixture.payments.size()).isEqualTo(1);
        assertThat(fixture.identity.generated).isEqualTo(1);
    }

    @Test
    void differentEventWithEquivalentSnapshotResolvesToTheSamePayment() {
        Fixture fixture = new Fixture();
        AcceptPaymentRequestResult first = fixture.service.accept(command(UUID.randomUUID(),
                NOW.plusSeconds(600), new BigDecimal("125.0000"), "VND"));
        AcceptPaymentRequestResult replay = fixture.service.accept(command(UUID.randomUUID(),
                NOW.plusSeconds(600), new BigDecimal("125.00"), "VND"));

        assertThat(first.fingerprint()).isEqualTo(replay.fingerprint());
        assertThat(replay.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.BUSINESS_REPLAYED);
        assertThat(replay.paymentId()).isEqualTo(first.paymentId());
        assertThat(fixture.payments.size()).isEqualTo(1);
    }

    @Test
    void contradictoryOrderSnapshotIsVisibleAndDoesNotMutatePayment() {
        Fixture fixture = new Fixture();
        AcceptPaymentRequestResult first = fixture.service.accept(command(UUID.randomUUID(),
                NOW.plusSeconds(600), new BigDecimal("125"), "VND"));
        AcceptPaymentRequestResult conflict = fixture.service.accept(command(UUID.randomUUID(),
                NOW.plusSeconds(600), new BigDecimal("250"), "VND"));

        assertThat(conflict.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.CONFLICT);
        assertThat(conflict.conflict()).isNotNull();
        assertThat(conflict.paymentId()).isEqualTo(first.paymentId());
        assertThat(fixture.payments.get(first.paymentId()).amount()).isEqualByComparingTo("125");
        assertThat(fixture.inbox.conflictedEventIds).hasSize(1);
    }

    @Test
    void firstProcessingAfterDeadlineCreatesExpiredPaymentAndStableFailureOutbox() {
        Fixture fixture = new Fixture();
        UUID eventId = UUID.randomUUID();

        AcceptPaymentRequestResult result = fixture.service.accept(command(eventId,
                NOW.minusSeconds(1), new BigDecimal("125"), "VND"));

        assertThat(result.outcome()).isEqualTo(AcceptPaymentRequestResult.Outcome.EXPIRED);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(fixture.payments.get(result.paymentId()).aggregateVersion()).isEqualTo(1);
        assertThat(fixture.outbox.records).hasSize(1);
        SavePaymentOutboxPort.OutboxRecord event = fixture.outbox.records.get(0);
        assertThat(event.eventType()).isEqualTo("PaymentFailed");
        assertThat(event.aggregateId()).isEqualTo(result.paymentId());
        assertThat(event.aggregateVersion()).isEqualTo(1);
        assertThat(event.payload()).contains("PAYMENT_DEADLINE_EXPIRED");
        assertThat(event.eventId()).isEqualTo(result.outboxEventId());
    }

    private AcceptPaymentRequestCommand command(UUID eventId, Instant deadline, BigDecimal amount,
            String currency) {
        return new AcceptPaymentRequestCommand(eventId, "PaymentRequested", 1, "order-service", "ORDER",
                ORDER_ID, 1, UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"), NOW, ORDER_ID, USER_ID, amount,
                currency, deadline, "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01", null);
    }

    private static final class Fixture {
        final InMemoryInbox inbox = new InMemoryInbox();
        final InMemoryPayments payments = new InMemoryPayments();
        final InMemoryOutbox outbox = new InMemoryOutbox();
        final FixedIdentity identity = new FixedIdentity();
        final AcceptPaymentRequestService service = new AcceptPaymentRequestService(inbox, payments, payments,
                outbox, () -> NOW, identity, new ImmediateTransactions());
    }

    private static final class FixedIdentity implements PaymentIdentityPort {
        private int generated;

        @Override
        public UUID newId() {
            generated++;
            return UUID.nameUUIDFromBytes(("payment-" + generated).getBytes());
        }
    }

    private static final class InMemoryPayments implements LoadPaymentPort, SavePaymentPort {
        final Map<UUID, Payment> values = new HashMap<>();

        Collection<Payment> values() {
            return values.values();
        }

        Payment get(UUID paymentId) {
            return values.get(paymentId);
        }

        int size() {
            return values.size();
        }

        @Override
        public Optional<Payment> findById(UUID paymentId) {
            return Optional.ofNullable(values.get(paymentId));
        }

        @Override
        public Optional<Payment> findByOrderId(UUID orderId) {
            return values.values().stream().filter(payment -> payment.orderId().equals(orderId)).findFirst();
        }

        @Override
        public Optional<Payment> findLockedById(UUID paymentId) {
            return findById(paymentId);
        }

        @Override
        public Payment save(Payment payment) {
            values.put(payment.id(), payment);
            return payment;
        }
    }

    private static final class InMemoryInbox implements PaymentCommandInboxPort {
        final Map<UUID, Receipt> byEvent = new HashMap<>();
        final Map<UUID, UUID> eventByOrder = new HashMap<>();
        final List<UUID> conflictedEventIds = new ArrayList<>();

        @Override
        public void lockOrder(UUID orderId) {
        }

        @Override
        public Optional<Receipt> findByEventId(UUID eventId) {
            return Optional.ofNullable(byEvent.get(eventId));
        }

        @Override
        public Optional<Receipt> findByOrderId(UUID orderId) {
            return Optional.ofNullable(eventByOrder.get(orderId)).map(byEvent::get);
        }

        @Override
        public Receipt receive(UUID eventId, String eventType, int eventVersion, UUID orderId,
                String payloadFingerprint, Instant receivedAt) {
            Receipt receipt = new Receipt(eventId, eventType, eventVersion, orderId, payloadFingerprint, null,
                    "RECEIVED", receivedAt, null);
            byEvent.put(eventId, receipt);
            eventByOrder.put(orderId, eventId);
            return receipt;
        }

        @Override
        public void markProcessed(UUID eventId, UUID paymentId, Instant processedAt) {
            Receipt existing = byEvent.get(eventId);
            byEvent.put(eventId, new Receipt(existing.eventId(), existing.eventType(), existing.eventVersion(),
                    existing.orderId(), existing.payloadFingerprint(), paymentId, "PROCESSED",
                    existing.receivedAt(), processedAt));
        }

        @Override
        public void markConflicted(UUID eventId, Instant observedAt) {
            Receipt existing = byEvent.get(eventId);
            byEvent.put(eventId, new Receipt(existing.eventId(), existing.eventType(), existing.eventVersion(),
                    existing.orderId(), existing.payloadFingerprint(), existing.paymentId(), "CONFLICTED",
                    existing.receivedAt(), observedAt));
            conflictedEventIds.add(eventId);
        }
    }

    private static final class InMemoryOutbox implements SavePaymentOutboxPort {
        final List<OutboxRecord> records = new ArrayList<>();

        @Override
        public OutboxRecord save(OutboxRecord record) {
            records.add(record);
            return record;
        }
    }

    private static final class ImmediateTransactions implements PaymentTransactionPort {
        @Override
        public <T> T execute(java.util.function.Supplier<T> work) {
            return work.get();
        }
    }
}
