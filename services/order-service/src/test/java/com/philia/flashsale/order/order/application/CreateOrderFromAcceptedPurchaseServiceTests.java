package com.philia.flashsale.order.order.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.exception.RetryableOrderPersistenceException;
import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.order.application.port.out.PersistOrderCreationPort;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.order.application.usecase.AcceptedPurchaseFingerprintService;
import com.philia.flashsale.order.order.application.usecase.CreateOrderFromAcceptedPurchaseService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateOrderFromAcceptedPurchaseServiceTests {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private FakePersistence persistence;
    private CreateOrderFromAcceptedPurchaseService service;

    @BeforeEach
    void setUp() {
        persistence = new FakePersistence();
        AtomicInteger sequence = new AtomicInteger();
        GenerateOrderIdentityPort identities = () -> UUID.nameUUIDFromBytes(
                ("identity-" + sequence.incrementAndGet()).getBytes());
        GenerateOrderNumberPort numbers = (createdAt, orderId) -> "FS-20300101-" + orderId;
        CurrentTimePort clock = () -> NOW;
        service = new CreateOrderFromAcceptedPurchaseService(persistence, identities, numbers, clock,
                new AcceptedPurchaseFingerprintService());
    }

    @Test
    void createdResultContainsStableOrderAndOutboxIdentities() {
        OrderCreationResult result = service.create(command(UUID.randomUUID()));

        assertThat(result.outcome()).isEqualTo(OrderCreationResult.Outcome.CREATED);
        assertThat(result.orderId()).isEqualTo(persistence.candidate.order().id());
        assertThat(result.outboxEventId()).isEqualTo(persistence.candidate.outboxEventId());
        assertThat(persistence.candidate.causationId()).isEqualTo(persistence.candidate.eventId());
        assertThat(persistence.candidate.order().line().quantity()).isEqualTo(2);
    }

    @Test
    void replayAndConflictOutcomesAreReturnedWithoutApplicationFrameworkTypes() {
        UUID eventId = UUID.randomUUID();
        persistence.next = OrderCreationResult.eventReplayed(UUID.randomUUID(), "a".repeat(64));
        assertThat(service.create(command(eventId)).outcome()).isEqualTo(OrderCreationResult.Outcome.EVENT_REPLAYED);

        persistence.next = OrderCreationResult.businessReplayed(UUID.randomUUID(), "b".repeat(64));
        assertThat(service.create(command(UUID.randomUUID())).outcome())
                .isEqualTo(OrderCreationResult.Outcome.BUSINESS_REPLAYED);

        persistence.next = OrderCreationResult.conflict(UUID.randomUUID(), "c".repeat(64), "contradiction");
        assertThat(service.create(command(UUID.randomUUID())).outcome()).isEqualTo(OrderCreationResult.Outcome.CONFLICT);
        assertThat(persistence.candidate.getClass().getPackageName())
                .isEqualTo("com.philia.flashsale.order.order.application.model");
    }

    @Test
    void retryablePersistenceFailureIsNotConvertedIntoSuccess() {
        persistence.failure = new RetryableOrderPersistenceException("database unavailable", null);

        assertThatThrownBy(() -> service.create(command(UUID.randomUUID())))
                .isSameAs(persistence.failure);
    }

    private static CreateOrderFromAcceptedPurchaseCommand command(UUID eventId) {
        return new CreateOrderFromAcceptedPurchaseCommand(eventId, "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", UUID.randomUUID(), 1, UUID.randomUUID(), null,
                NOW, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                2, new BigDecimal("10.0000"), "VND", NOW, NOW.plusSeconds(300),
                "flashsale.purchase.events.v1", 0, 1, "00-abc-def-01", null);
    }

    private static final class FakePersistence implements PersistOrderCreationPort {
        private OrderCreationCandidate candidate;
        private OrderCreationResult next;
        private RuntimeException failure;

        @Override
        public OrderCreationResult persist(OrderCreationCandidate candidate) {
            this.candidate = candidate;
            if (failure != null) {
                throw failure;
            }
            return next == null
                    ? OrderCreationResult.created(candidate.order().id(), candidate.outboxEventId(), candidate.fingerprint())
                    : next;
        }
    }
}
