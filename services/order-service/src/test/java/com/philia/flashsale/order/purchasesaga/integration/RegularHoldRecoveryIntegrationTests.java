package com.philia.flashsale.order.purchasesaga.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderLine;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldConfirmedLine;
import com.philia.flashsale.order.purchasesaga.application.command.RegularStockHoldOutcomeCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentFailureUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyRegularHoldOutcomeUseCase;
import com.philia.flashsale.order.purchasesaga.application.exception.InvalidRegularHoldOutcomeException;
import com.philia.flashsale.order.purchasesaga.application.result.RegularHoldOutcomeResult;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentFailureResult;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.RegularPurchasePersistenceAdapter;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseLine;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import com.philia.flashsale.order.support.PostgreSqlIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** Proves the atomic Order/Saga/inbox/outbox boundary for regular hold recovery facts. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.outbox-publisher-enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.payment-events-consumer-enabled=false",
        "order.runtime.purchase-reservation-results-consumer-enabled=false",
        "order.regular-purchase.runtime.hold-result-consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RegularHoldRecoveryIntegrationTests extends PostgreSqlIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2032-01-01T10:00:00Z");
    private static final AtomicLong OFFSET = new AtomicLong(700_000);

    @Autowired private RegularPurchasePersistenceAdapter intake;
    @Autowired private ApplyPaymentFailureUseCase paymentFailures;
    @Autowired private ApplyPaymentSuccessUseCase paymentSuccesses;
    @Autowired private ApplyRegularHoldOutcomeUseCase outcomes;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void releasedFactTerminalizesRegularOrderAndReplayDoesNotRepublish() {
        Fixture fixture = pending();
        PaymentFailedCommand failed = paymentFailed(fixture, "PROVIDER_TERMINAL_FAILURE");
        PaymentFailureResult failure = paymentFailures.apply(failed);
        assertThat(failure.outcome()).isEqualTo(PaymentFailureResult.Outcome.APPLIED);
        assertThat(status("purchase_sagas", "order_id", fixture.orderId())).isEqualTo("RELEASING_STOCK");

        RegularStockHoldOutcomeCommand released = outcome(fixture, failed, failure.releaseCommandId(),
                "RELEASED", "PROVIDER_TERMINAL_FAILURE");
        assertThat(outcomes.apply(released).outcome()).isEqualTo(RegularHoldOutcomeResult.Outcome.APPLIED);
        assertThat(outcomes.apply(released).outcome()).isEqualTo(RegularHoldOutcomeResult.Outcome.REPLAYED);
        assertThat(status("orders", "id", fixture.orderId())).isEqualTo("CANCELLED");
        assertThat(status("purchase_sagas", "order_id", fixture.orderId())).isEqualTo("COMPENSATED");
        assertThat(outboxCount(fixture.orderId(), "OrderCancelledV2")).isEqualTo(1);
        assertThat(inboxCount(released.eventId())).isEqualTo(1);
    }

    @Test
    void expiredFactTerminalizesPendingRegularOrderExactlyOnce() {
        Fixture fixture = pending();
        RegularStockHoldOutcomeCommand expired = outcome(fixture, null, fixture.requestId(),
                "EXPIRED", null);

        assertThat(outcomes.apply(expired).outcome()).isEqualTo(RegularHoldOutcomeResult.Outcome.APPLIED);
        assertThat(status("orders", "id", fixture.orderId())).isEqualTo("EXPIRED");
        assertThat(status("purchase_sagas", "order_id", fixture.orderId())).isEqualTo("COMPENSATED");
        assertThat(outboxCount(fixture.orderId(), "OrderExpiredV2")).isEqualTo(1);
        assertThat(outcomes.apply(expired).outcome()).isEqualTo(RegularHoldOutcomeResult.Outcome.REPLAYED);
    }

    @Test
    void releasedFactWithWrongActiveCommandIsRejectedWithoutStateMutation() {
        Fixture fixture = pending();
        PaymentFailedCommand failed = paymentFailed(fixture, "PROVIDER_TERMINAL_FAILURE");
        PaymentFailureResult failure = paymentFailures.apply(failed);
        RegularStockHoldOutcomeCommand released = outcome(fixture, failed, UUID.randomUUID(),
                "RELEASED", "PROVIDER_TERMINAL_FAILURE");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> outcomes.apply(released))
                .isInstanceOf(InvalidRegularHoldOutcomeException.class)
                .hasMessageContaining("active release command");
        assertThat(status("purchase_sagas", "order_id", fixture.orderId())).isEqualTo("RELEASING_STOCK");
        assertThat(inboxCount(released.eventId())).isZero();
        assertThat(failure.releaseCommandId()).isNotEqualTo(released.causationId());
    }

    @Test
    void releaseFactAfterVerifiedPaymentSuccessMovesSagaToManualReview() {
        Fixture fixture = pending();
        var paymentId = UUID.randomUUID();
        var paymentEventId = UUID.randomUUID();
        var success = new com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand(
                paymentEventId, "PaymentSucceeded", 1, "payment-service", "PAYMENT", paymentId, 2,
                fixture.orderId(), UUID.randomUUID(), NOW.plusSeconds(5), paymentId, fixture.orderId(),
                new BigDecimal("179000.0000"), "VND", NOW.plusSeconds(5), "stripe", "cs_test", null,
                "flashsale.payment.events.v1", 0, OFFSET.incrementAndGet(), null, null, "a".repeat(64));
        assertThat(paymentSuccesses.apply(success).outcome().name()).isEqualTo("APPLIED");

        RegularStockHoldOutcomeCommand released = outcome(fixture, null, UUID.randomUUID(),
                "RELEASED", "PROVIDER_TERMINAL_FAILURE");
        assertThat(outcomes.apply(released).outcome()).isEqualTo(RegularHoldOutcomeResult.Outcome.MANUAL_REVIEW);
        assertThat(status("purchase_sagas", "order_id", fixture.orderId())).isEqualTo("MANUAL_REVIEW");
        assertThat(status("orders", "id", fixture.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(outboxCount(fixture.orderId(), "OrderPaymentReviewRequired")).isEqualTo(1);
    }

    private Fixture pending() {
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID shopperId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Money price = Money.of(new BigDecimal("179000.0000"));
        RegularPurchaseRequest registered = RegularPurchaseRequest.receiveBuyNow(requestId, shopperId,
                "buy-" + requestId, orderId, holdId,
                new RegularPurchaseLine(variantId, 1, price, "VND", null), NOW);
        intake.register(registered);
        Instant expiry = NOW.plusSeconds(300);
        var accepted = registered.productValidated(NOW.plusSeconds(1))
                .holdAcquired(expiry, NOW.plusSeconds(2)).accept(orderId, NOW.plusSeconds(3));
        Order order = Order.regular(orderId, "REG-" + orderId, requestId, PurchaseSource.BUY_NOW, holdId,
                shopperId, "VND", List.of(OrderLine.create(UUID.randomUUID(), variantId, 1, price)),
                null, null, NOW.plusSeconds(3), expiry);
        PurchaseSaga saga = PurchaseSaga.startRegular(orderId, requestId, holdId, expiry, NOW.plusSeconds(3));
        intake.accept(new RegularPurchaseAcceptance(accepted, order, saga, UUID.randomUUID(), UUID.randomUUID(),
                requestId, requestId, NOW.plusSeconds(3), null, null));
        return new Fixture(requestId, orderId, holdId, variantId);
    }

    private PaymentFailedCommand paymentFailed(Fixture fixture, String reason) {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        return new PaymentFailedCommand(eventId, "PaymentFailed", 1, "payment-service", "PAYMENT", paymentId, 1,
                fixture.orderId(), eventId, NOW.plusSeconds(4), paymentId, fixture.orderId(),
                new BigDecimal("179000.0000"), "VND", NOW.plusSeconds(4), reason, "stripe", "cs_test_failure",
                "flashsale.payment.events.v1", 0, OFFSET.incrementAndGet(), null, null, "a".repeat(64));
    }

    private RegularStockHoldOutcomeCommand outcome(Fixture fixture, PaymentFailedCommand failed,
            UUID causationId, String status, String reason) {
        UUID eventId = UUID.randomUUID();
        Instant transitionedAt = NOW.plusSeconds(6);
        return new RegularStockHoldOutcomeCommand(eventId,
                "RELEASED".equals(status) ? "RegularStockHoldReleased" : "RegularStockHoldExpired", 1,
                "inventory-service", "REGULAR_STOCK_HOLD", fixture.holdId(), 2,
                fixture.requestId(), causationId, transitionedAt, fixture.requestId(), fixture.orderId(),
                fixture.requestId(), fixture.holdId(), status, reason, transitionedAt,
                List.of(new RegularStockHoldConfirmedLine(fixture.variantId(), 1)),
                "flashsale.inventory.regular-hold.events.v1", 0, OFFSET.incrementAndGet(), null, null,
                "b".repeat(64));
    }

    private String status(String table, String column, UUID id) {
        return jdbc.queryForObject("select status from " + table + " where " + column + " = ?", String.class, id);
    }

    private long outboxCount(UUID orderId, String eventType) {
        return jdbc.queryForObject("select count(*) from order_outbox_events where aggregate_id = ? and event_type = ?",
                Long.class, orderId, eventType);
    }

    private long inboxCount(UUID eventId) {
        return jdbc.queryForObject("select count(*) from purchase_saga_inbox where event_id = ?", Long.class, eventId);
    }

    private record Fixture(UUID requestId, UUID orderId, UUID holdId, UUID variantId) { }
}
