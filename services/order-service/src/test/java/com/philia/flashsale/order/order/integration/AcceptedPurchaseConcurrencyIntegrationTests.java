package com.philia.flashsale.order.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.support.PostgreSqlIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** Proves transaction-scoped Order/Saga identity arbitration across concurrent service workers. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.outbox-publisher-enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "spring.datasource.hikari.maximum-pool-size=24"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AcceptedPurchaseConcurrencyIntegrationTests extends PostgreSqlIntegrationTestSupport {

    private static final Instant ACCEPTED = Instant.parse("2030-01-01T10:00:00Z");
    private static final AtomicLong OFFSET = new AtomicLong(1000);

    @Autowired
    private CreateOrderFromAcceptedPurchaseUseCase useCase;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void oneHundredEquivalentDeliveriesCreateOneLogicalOrderAndOutboxFact() throws Exception {
        CreateOrderFromAcceptedPurchaseCommand base = command(UUID.randomUUID(), 2);
        List<Callable<OrderCreationResult>> calls = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            calls.add(() -> useCase.create(copy(base, UUID.randomUUID(), base.quantity())));
        }

        List<OrderCreationResult> results = run(calls);

        assertThat(results).extracting(OrderCreationResult::outcome)
                .contains(OrderCreationResult.Outcome.CREATED)
                .allMatch(outcome -> outcome == OrderCreationResult.Outcome.CREATED
                        || outcome == OrderCreationResult.Outcome.BUSINESS_REPLAYED);
        UUID orderId = results.stream().map(OrderCreationResult::orderId).distinct().findFirst().orElseThrow();
        assertThat(results).allMatch(result -> result.orderId().equals(orderId));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE purchase_request_id = ?", Long.class,
                base.purchaseRequestId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_lines WHERE order_id = ?", Long.class, orderId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_consumer_inbox WHERE purchase_request_id = ?",
                Long.class, base.purchaseRequestId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events WHERE aggregate_id = ?", Long.class,
                orderId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_sagas WHERE order_id = ?", Long.class,
                orderId)).isEqualTo(1L);
    }

    @Test
    void contradictoryConcurrentDeliveryNeverMutatesTheWinner() throws Exception {
        CreateOrderFromAcceptedPurchaseCommand accepted = command(UUID.randomUUID(), 2);
        CreateOrderFromAcceptedPurchaseCommand contradictory = copy(accepted, UUID.randomUUID(), 3);
        List<Callable<OrderCreationResult>> calls = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            calls.add(() -> useCase.create(copy(accepted, UUID.randomUUID(), 2)));
            calls.add(() -> useCase.create(copy(contradictory, UUID.randomUUID(), 3)));
        }

        List<OrderCreationResult> results = run(calls);

        assertThat(results).extracting(OrderCreationResult::outcome)
                .contains(OrderCreationResult.Outcome.CREATED, OrderCreationResult.Outcome.CONFLICT)
                .allMatch(outcome -> outcome == OrderCreationResult.Outcome.CREATED
                        || outcome == OrderCreationResult.Outcome.BUSINESS_REPLAYED
                        || outcome == OrderCreationResult.Outcome.CONFLICT);
        UUID orderId = results.stream().filter(result -> result.outcome() == OrderCreationResult.Outcome.CREATED)
                .map(OrderCreationResult::orderId).findFirst().orElseGet(() -> results.get(0).orderId());
        assertThat(jdbc.queryForObject("SELECT quantity FROM order_lines WHERE order_id = ?", Long.class, orderId))
                .isIn(2L, 3L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE purchase_request_id = ?", Long.class,
                accepted.purchaseRequestId())).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_lines WHERE order_id = ?", Long.class, orderId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events WHERE aggregate_id = ?", Long.class,
                orderId)).isEqualTo(2L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_sagas WHERE order_id = ?", Long.class,
                orderId)).isEqualTo(1L);
    }

    private List<OrderCreationResult> run(List<Callable<OrderCreationResult>> calls) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            List<Future<OrderCreationResult>> futures = executor.invokeAll(calls);
            List<OrderCreationResult> results = new ArrayList<>();
            for (Future<OrderCreationResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private static CreateOrderFromAcceptedPurchaseCommand command(UUID eventId, long quantity) {
        UUID purchaseRequestId = UUID.randomUUID();
        return new CreateOrderFromAcceptedPurchaseCommand(eventId, "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1, UUID.randomUUID(), null,
                ACCEPTED, purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                quantity, new BigDecimal("10.0000"), "VND", ACCEPTED, ACCEPTED.plusSeconds(300),
                "flashsale.purchase.events.v1", 0, OFFSET.incrementAndGet(), null, null);
    }

    private static CreateOrderFromAcceptedPurchaseCommand copy(
            CreateOrderFromAcceptedPurchaseCommand source, UUID eventId, long quantity) {
        return new CreateOrderFromAcceptedPurchaseCommand(eventId, source.eventType(), source.eventVersion(),
                source.producer(), source.aggregateType(), source.aggregateId(), source.aggregateVersion(),
                source.correlationId(), source.causationId(), source.eventOccurredAt(), source.purchaseRequestId(),
                source.reservationId(), source.campaignId(), source.variantId(), source.userId(), quantity,
                source.unitPrice(), source.currency(), source.acceptedAt(), source.expiresAt(), source.sourceTopic(),
                source.sourcePartition(), OFFSET.incrementAndGet(), source.traceparent(), source.tracestate());
    }
}
