package com.philia.flashsale.order.purchasesaga.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentFailureUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationReleaseUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentFailureResult;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationReleaseResult;
import com.philia.flashsale.order.support.PostgreSqlIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** Verifies the durable PaymentFailed -> release -> terminal Order transition. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.outbox-publisher-enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.payment-events-consumer-enabled=false",
        "order.runtime.purchase-reservation-results-consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentFailureTransitionIntegrationTests extends PostgreSqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final AtomicLong OFFSET = new AtomicLong();

    @Autowired
    private CreateOrderFromAcceptedPurchaseUseCase createOrder;

    @Autowired
    private ApplyPaymentFailureUseCase applyPaymentFailure;

    @Autowired
    private ApplyPurchaseReservationReleaseUseCase applyReservationRelease;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void providerTerminalFailureCreatesOneReleaseAndCompensatesOrderExactlyOnce() {
        CreateOrderFromAcceptedPurchaseCommand accepted = acceptedCommand();
        OrderCreationResult created = createOrder.create(accepted);
        UUID paymentId = UUID.randomUUID();
        PaymentFailedCommand failed = paymentFailed(created.orderId(), paymentId,
                "PROVIDER_TERMINAL_FAILURE");

        PaymentFailureResult failure = applyPaymentFailure.apply(failed);
        assertThat(failure.outcome()).isEqualTo(PaymentFailureResult.Outcome.APPLIED);
        assertThat(applyPaymentFailure.apply(failed).outcome())
                .isEqualTo(PaymentFailureResult.Outcome.REPLAYED);
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_sagas WHERE order_id = ?",
                String.class, created.orderId())).isEqualTo("RELEASING_RESERVATION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events "
                        + "WHERE event_type = ? AND aggregate_id = ?", Long.class,
                "ReleasePurchaseReservation", created.purchaseSagaId())).isEqualTo(1);

        PurchaseReservationReleasedCommand released = released(created, accepted, failed,
                "RELEASED", "PROVIDER_TERMINAL_FAILURE");
        PurchaseReservationReleaseResult release = applyReservationRelease.apply(released);
        assertThat(release.outcome()).isEqualTo(PurchaseReservationReleaseResult.Outcome.APPLIED);
        assertThat(applyReservationRelease.apply(released).outcome())
                .isEqualTo(PurchaseReservationReleaseResult.Outcome.REPLAYED);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class,
                created.orderId())).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_sagas WHERE order_id = ?",
                String.class, created.orderId())).isEqualTo("COMPENSATED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events "
                        + "WHERE event_type = ? AND aggregate_id = ?", Long.class,
                "OrderCancelled", created.orderId())).isEqualTo(1);
    }

    @Test
    void paymentDeadlineFailureSelectsExpiredOrderTerminalState() {
        CreateOrderFromAcceptedPurchaseCommand accepted = acceptedCommand();
        OrderCreationResult created = createOrder.create(accepted);
        PaymentFailedCommand failed = paymentFailed(created.orderId(), UUID.randomUUID(),
                "PAYMENT_DEADLINE_EXPIRED");

        assertThat(applyPaymentFailure.apply(failed).desiredOrderStatus()).isEqualTo("EXPIRED");
        PurchaseReservationReleasedCommand released = released(created, accepted, failed,
                "EXPIRED", "PAYMENT_DEADLINE_EXPIRED");
        assertThat(applyReservationRelease.apply(released).outcome())
                .isEqualTo(PurchaseReservationReleaseResult.Outcome.APPLIED);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class,
                created.orderId())).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events "
                        + "WHERE event_type = ? AND aggregate_id = ?", Long.class,
                "OrderExpired", created.orderId())).isEqualTo(1);
    }

    private CreateOrderFromAcceptedPurchaseCommand acceptedCommand() {
        UUID purchaseRequestId = UUID.randomUUID();
        return new CreateOrderFromAcceptedPurchaseCommand(UUID.randomUUID(), "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1, UUID.randomUUID(), null,
                NOW, purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 2, new BigDecimal("10.0000"), "VND", NOW, NOW.plusSeconds(300),
                "flashsale.purchase.events.v1", 0, OFFSET.incrementAndGet(), null, null);
    }

    private PaymentFailedCommand paymentFailed(UUID orderId, UUID paymentId, String reason) {
        UUID eventId = UUID.randomUUID();
        return new PaymentFailedCommand(eventId, "PaymentFailed", 1, "payment-service", "PAYMENT",
                paymentId, 1, orderId, eventId, NOW.plusSeconds(1), paymentId, orderId,
                new BigDecimal("20.0000"), "VND", NOW.plusSeconds(1), reason, "stripe",
                "cs_test_failure", "flashsale.payment.events.v1", 0, OFFSET.incrementAndGet(),
                null, null, "a".repeat(64));
    }

    private PurchaseReservationReleasedCommand released(OrderCreationResult created,
            CreateOrderFromAcceptedPurchaseCommand accepted, PaymentFailedCommand failed,
            String status, String reason) {
        return new PurchaseReservationReleasedCommand(UUID.randomUUID(),
                "PurchaseReservationReleased", 1, "flashsale-service", "PURCHASE_RESERVATION",
                accepted.reservationId(), 1, accepted.purchaseRequestId(), failed.eventId(), NOW.plusSeconds(2),
                accepted.purchaseRequestId(), created.orderId(), accepted.purchaseRequestId(),
                accepted.reservationId(), status, reason, NOW.plusSeconds(2),
                "flashsale.purchase.events.v1", 0, OFFSET.incrementAndGet(), null, null,
                "b".repeat(64));
    }
}
