package com.philia.flashsale.order.purchasesaga.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationConfirmedCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationConfirmationUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;
import com.philia.flashsale.order.purchasesaga.application.result.PurchaseReservationConfirmationResult;
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

/** Verifies the atomic Order terminalization after Flash Sale confirms a reservation. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.outbox-publisher-enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PurchaseReservationConfirmationPersistenceIntegrationTests extends PostgreSqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final AtomicLong OFFSET = new AtomicLong();

    @Autowired
    private CreateOrderFromAcceptedPurchaseUseCase createOrder;

    @Autowired
    private ApplyPaymentSuccessUseCase applyPaymentSuccess;

    @Autowired
    private ApplyPurchaseReservationConfirmationUseCase applyReservationConfirmation;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void confirmationCommitsOrderSagaInboxAndOrderConfirmedOutboxAndReplayIsNoOp() {
        UUID purchaseRequestId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        OrderCreationResult created = createOrder.create(acceptedCommand(purchaseRequestId, reservationId));
        UUID paymentId = UUID.randomUUID();

        PaymentSucceededCommand payment = paymentSucceeded(created.orderId(), purchaseRequestId, paymentId);
        PaymentSuccessResult paymentResult = applyPaymentSuccess.apply(payment);
        assertThat(paymentResult.outcome()).isEqualTo(PaymentSuccessResult.Outcome.APPLIED);

        PurchaseReservationConfirmedCommand confirmation = confirmation(
                created.orderId(), purchaseRequestId, reservationId, paymentId, paymentResult.confirmCommandId());
        PurchaseReservationConfirmationResult result = applyReservationConfirmation.apply(confirmation);
        assertThat(result.outcome()).isEqualTo(PurchaseReservationConfirmationResult.Outcome.APPLIED);
        assertThat(applyReservationConfirmation.apply(confirmation).outcome())
                .isEqualTo(PurchaseReservationConfirmationResult.Outcome.REPLAYED);

        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, created.orderId()))
                .isEqualTo("CONFIRMED");
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_sagas WHERE order_id = ?", String.class,
                created.orderId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_saga_inbox WHERE event_id = ?", Long.class,
                confirmation.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events WHERE event_type = ? AND aggregate_id = ?",
                Long.class, "OrderConfirmed", created.orderId())).isEqualTo(1);
    }

    private CreateOrderFromAcceptedPurchaseCommand acceptedCommand(UUID purchaseRequestId, UUID reservationId) {
        return new CreateOrderFromAcceptedPurchaseCommand(UUID.randomUUID(), "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1, reservationId, null, NOW,
                purchaseRequestId, reservationId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 2,
                new BigDecimal("10.0000"), "VND", NOW, NOW.plusSeconds(300),
                "flashsale.purchase.events.v1", 0, OFFSET.incrementAndGet(), null, null);
    }

    private PaymentSucceededCommand paymentSucceeded(UUID orderId, UUID sagaId, UUID paymentId) {
        return new PaymentSucceededCommand(UUID.randomUUID(), "PaymentSucceeded", 1, "payment-service", "PAYMENT",
                paymentId, 1, sagaId, UUID.randomUUID(), NOW.plusSeconds(1), paymentId, orderId,
                new BigDecimal("20.0000"), "VND", NOW.plusSeconds(1), "stripe", "cs_test_session", null,
                "flashsale.payment.events.v1", 0, OFFSET.incrementAndGet(), null, null, "a".repeat(64));
    }

    private PurchaseReservationConfirmedCommand confirmation(UUID orderId, UUID sagaId, UUID reservationId,
            UUID paymentId, UUID causationId) {
        return new PurchaseReservationConfirmedCommand(UUID.randomUUID(), "PurchaseReservationConfirmed", 1,
                "flashsale-service", "PURCHASE_RESERVATION", reservationId, 1, sagaId, causationId,
                NOW.plusSeconds(2), sagaId, orderId, sagaId, reservationId, paymentId, NOW.plusSeconds(2),
                "flashsale.purchase.events.v1", 0, OFFSET.incrementAndGet(), null, null, "b".repeat(64));
    }
}
