package com.philia.flashsale.order.order.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.OrderCreationJpaAdapter;
import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.exception.RetryableOrderPersistenceException;
import com.philia.flashsale.order.order.application.model.OrderCreationCandidate;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderLine;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.support.PostgreSqlIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** Verifies atomic Order, Purchase Saga, inbox, and dual-outbox creation against PostgreSQL. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.outbox-publisher-enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AcceptedPurchasePersistenceIntegrationTests extends PostgreSqlIntegrationTestSupport {

    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");
    private static final AtomicLong OFFSET = new AtomicLong();

    @Autowired
    private CreateOrderFromAcceptedPurchaseUseCase useCase;

    @Autowired
    private OrderCreationJpaAdapter persistence;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void newAcceptedPurchaseCommitsOrderLineInboxAndOutboxTogether() {
        CreateOrderFromAcceptedPurchaseCommand command = command(UUID.randomUUID());

        OrderCreationResult result = useCase.create(command);

        assertThat(result.outcome()).isEqualTo(OrderCreationResult.Outcome.CREATED);
        assertThat(count("orders", "purchase_request_id", command.purchaseRequestId())).isEqualTo(1);
        assertThat(count("order_lines", "order_id", result.orderId())).isEqualTo(1);
        assertThat(count("order_consumer_inbox", "event_id", command.eventId())).isEqualTo(1);
        assertThat(count("order_outbox_events", "event_id", result.outboxEventId())).isEqualTo(1);
        assertThat(result.purchaseSagaId()).isEqualTo(command.purchaseRequestId());
        assertThat(result.paymentRequestedOutboxEventId()).isNotNull();
        assertThat(count("purchase_sagas", "id", command.purchaseRequestId())).isEqualTo(1);
        assertThat(count("order_outbox_events", "event_id", result.paymentRequestedOutboxEventId())).isEqualTo(1);
        Map<String, Object> paymentOutbox = jdbc.queryForMap("""
                SELECT aggregate_type, aggregate_id, event_key, event_type, payload::text AS payload
                FROM order_outbox_events WHERE event_id = ?
                """, result.paymentRequestedOutboxEventId());
        assertThat(paymentOutbox.get("aggregate_type")).isEqualTo("PURCHASE_SAGA");
        assertThat(paymentOutbox.get("aggregate_id")).isEqualTo(result.orderId());
        assertThat(paymentOutbox.get("event_key")).isEqualTo(result.orderId().toString());
        assertThat(paymentOutbox.get("event_type")).isEqualTo("PaymentRequested");
        assertThat(paymentOutbox.get("payload").toString()).contains("paymentDeadline");
        Map<String, Object> snapshot = jdbc.queryForMap("""
                SELECT o.status, o.currency, o.subtotal_amount, l.quantity, l.unit_price, l.line_amount
                FROM orders o JOIN order_lines l ON l.order_id = o.id
                WHERE o.id = ?
                """, result.orderId());
        assertThat(snapshot.get("status")).isEqualTo("PENDING_PAYMENT");
        assertThat(snapshot.get("currency")).isEqualTo("VND");
        assertThat(((BigDecimal) snapshot.get("subtotal_amount"))).isEqualByComparingTo("20.0000");
        assertThat(snapshot.get("quantity")).isEqualTo(2L);
        assertThat(((BigDecimal) snapshot.get("line_amount"))).isEqualByComparingTo("20.0000");
    }

    @Test
    void sameEventReplayIsNoOpAndDifferentEventEquivalentReplayReusesTheOrder() {
        UUID eventId = UUID.randomUUID();
        CreateOrderFromAcceptedPurchaseCommand first = command(eventId);
        OrderCreationResult created = useCase.create(first);

        OrderCreationResult sameEvent = useCase.create(first);
        CreateOrderFromAcceptedPurchaseCommand differentEvent = copyWithEvent(first, UUID.randomUUID());
        OrderCreationResult businessReplay = useCase.create(differentEvent);

        assertThat(sameEvent.outcome()).isEqualTo(OrderCreationResult.Outcome.EVENT_REPLAYED);
        assertThat(businessReplay.outcome()).isEqualTo(OrderCreationResult.Outcome.BUSINESS_REPLAYED);
        assertThat(businessReplay.orderId()).isEqualTo(created.orderId());
        assertThat(count("orders", "purchase_request_id", first.purchaseRequestId())).isEqualTo(1);
        assertThat(count("order_consumer_inbox", "purchase_request_id", first.purchaseRequestId())).isEqualTo(1);
        assertThat(count("order_outbox_events", "aggregate_id", created.orderId())).isEqualTo(2);
        assertThat(count("purchase_sagas", "order_id", created.orderId())).isEqualTo(1);
    }

    @Test
    void contradictoryIdentityReplayDoesNotMutateTheWinner() {
        UUID eventId = UUID.randomUUID();
        CreateOrderFromAcceptedPurchaseCommand first = command(eventId);
        OrderCreationResult created = useCase.create(first);
        CreateOrderFromAcceptedPurchaseCommand contradictory = new CreateOrderFromAcceptedPurchaseCommand(
                UUID.randomUUID(), first.eventType(), first.eventVersion(), first.producer(), first.aggregateType(),
                first.aggregateId(), first.aggregateVersion(), first.correlationId(), null, first.eventOccurredAt(),
                first.purchaseRequestId(), first.reservationId(), first.campaignId(), first.variantId(),
                first.userId(), 3, first.unitPrice(), first.currency(), first.acceptedAt(), first.expiresAt(),
                first.sourceTopic(), first.sourcePartition(), OFFSET.incrementAndGet(), null, null);

        OrderCreationResult conflict = useCase.create(contradictory);

        assertThat(conflict.outcome()).isEqualTo(OrderCreationResult.Outcome.CONFLICT);
        assertThat(conflict.orderId()).isEqualTo(created.orderId());
        assertThat(jdbc.queryForObject("SELECT quantity FROM order_lines WHERE order_id = ?", Long.class, created.orderId()))
                .isEqualTo(2L);
        assertThat(count("order_consumer_inbox", "purchase_request_id", first.purchaseRequestId())).isEqualTo(1);
    }

    @Test
    void orderNumberCollisionRollsBackAllDurableRows() {
        CreateOrderFromAcceptedPurchaseCommand first = command(UUID.randomUUID());
        OrderCreationResult created = useCase.create(first);
        String existingNumber = jdbc.queryForObject("SELECT order_number FROM orders WHERE id = ?", String.class,
                created.orderId());
        UUID orderId = UUID.randomUUID();
        OrderLine line = OrderLine.create(UUID.randomUUID(), UUID.randomUUID(), 1,
                Money.of(new BigDecimal("1.0000")));
        Order order = Order.create(orderId, existingNumber, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "VND", line, ACCEPTED, ACCEPTED.plusSeconds(300));
        OrderCreationCandidate candidate = new OrderCreationCandidate(
                UUID.randomUUID(), "PurchaseAccepted", 1, "flashsale-service", "PURCHASE_REQUEST",
                order.purchaseRequestId(), 1, UUID.randomUUID(), UUID.randomUUID(), ACCEPTED, order,
                UUID.randomUUID(), "d".repeat(64), null, null, "flashsale.purchase.events.v1", 0,
                OFFSET.incrementAndGet(), ACCEPTED);

        assertThatThrownBy(() -> persistence.persist(candidate))
                .isInstanceOf(RetryableOrderPersistenceException.class);
        assertThat(count("orders", "id", orderId)).isZero();
        assertThat(count("order_lines", "order_id", orderId)).isZero();
        assertThat(count("order_consumer_inbox", "event_id", candidate.eventId())).isZero();
        assertThat(count("order_outbox_events", "event_id", candidate.outboxEventId())).isZero();
        assertThat(count("orders", "id", created.orderId())).isEqualTo(1);
    }

    @Test
    void outboxSnapshotKeepsStableIdentityAndAcceptedValues() {
        CreateOrderFromAcceptedPurchaseCommand command = command(UUID.randomUUID());

        OrderCreationResult result = useCase.create(command);

        Map<String, Object> outbox = jdbc.queryForMap("""
                SELECT event_id, aggregate_id, event_key, event_type, status, payload::text AS payload
                FROM order_outbox_events WHERE event_id = ?
                """, result.outboxEventId());
        assertThat(outbox.get("event_id")).isEqualTo(result.outboxEventId());
        assertThat(outbox.get("aggregate_id")).isEqualTo(result.orderId());
        assertThat(outbox.get("event_key")).isEqualTo(result.orderId().toString());
        assertThat(outbox.get("event_type")).isEqualTo("OrderCreated");
        assertThat(outbox.get("status")).isEqualTo("PENDING");
        String payload = outbox.get("payload").toString().replaceAll("\\s+", "");
        assertThat(payload).contains(
                "\"orderId\":\"" + result.orderId(),
                "\"purchaseRequestId\":\"" + command.purchaseRequestId(),
                "\"quantity\":2");
    }

    private CreateOrderFromAcceptedPurchaseCommand command(UUID eventId) {
        UUID purchaseRequestId = UUID.randomUUID();
        return new CreateOrderFromAcceptedPurchaseCommand(eventId, "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1, UUID.randomUUID(), null,
                ACCEPTED, purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                2, new BigDecimal("10.0000"), "VND", ACCEPTED, ACCEPTED.plusSeconds(300),
                "flashsale.purchase.events.v1", 0, OFFSET.incrementAndGet(), null, null);
    }

    private static CreateOrderFromAcceptedPurchaseCommand copyWithEvent(
            CreateOrderFromAcceptedPurchaseCommand source, UUID eventId) {
        return new CreateOrderFromAcceptedPurchaseCommand(eventId, source.eventType(), source.eventVersion(),
                source.producer(), source.aggregateType(), source.aggregateId(), source.aggregateVersion(),
                source.correlationId(), source.causationId(), source.eventOccurredAt(), source.purchaseRequestId(),
                source.reservationId(), source.campaignId(), source.variantId(), source.userId(), source.quantity(),
                source.unitPrice(), source.currency(), source.acceptedAt(), source.expiresAt(), source.sourceTopic(),
                source.sourcePartition(), source.sourceOffset() + 100, source.traceparent(), source.tracestate());
    }

    private long count(String table, String column, UUID value) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, value);
    }
}
