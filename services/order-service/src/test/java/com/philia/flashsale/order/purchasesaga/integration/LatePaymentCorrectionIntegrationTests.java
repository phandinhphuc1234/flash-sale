package com.philia.flashsale.order.purchasesaga.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentFailedCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.command.PurchaseReservationReleasedCommand;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentFailureUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPurchaseReservationReleaseUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentFailureResult;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;
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

/** Verifies success-dominant late payment correction after an unpaid terminal path. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.outbox-publisher-enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.payment-events-consumer-enabled=false",
        "order.runtime.purchase-reservation-results-consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LatePaymentCorrectionIntegrationTests extends PostgreSqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final AtomicLong OFFSET = new AtomicLong();

    @Autowired
    private CreateOrderFromAcceptedPurchaseUseCase createOrder;

    @Autowired
    private ApplyPaymentFailureUseCase applyPaymentFailure;

    @Autowired
    private ApplyPurchaseReservationReleaseUseCase applyReservationRelease;

    @Autowired
    private ApplyPaymentSuccessUseCase applyPaymentSuccess;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void lateSuccessReopensTerminalOrderAndWritesOneStableReviewCorrection() {
        CreateOrderFromAcceptedPurchaseCommand accepted = acceptedCommand();
        OrderCreationResult created = createOrder.create(accepted);
        UUID failedPaymentId = UUID.randomUUID();
        PaymentFailedCommand failed = paymentFailed(created.orderId(), failedPaymentId);

        assertThat(applyPaymentFailure.apply(failed).outcome())
                .isEqualTo(PaymentFailureResult.Outcome.APPLIED);
        PurchaseReservationReleasedCommand released = released(created, accepted, failed);
        assertThat(applyReservationRelease.apply(released).outcome())
                .isEqualTo(PurchaseReservationReleaseResult.Outcome.APPLIED);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class,
                created.orderId())).isEqualTo("CANCELLED");

        PaymentSucceededCommand lateSuccess = lateSuccess(created.orderId(), failedPaymentId);
        PaymentSuccessResult result = applyPaymentSuccess.apply(lateSuccess);
        assertThat(result.outcome()).isEqualTo(PaymentSuccessResult.Outcome.MANUAL_REVIEW);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class,
                created.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_sagas WHERE order_id = ?",
                String.class, created.orderId())).isEqualTo("MANUAL_REVIEW");
        assertThat(jdbc.queryForObject("SELECT manual_review_reason FROM purchase_sagas WHERE order_id = ?",
                String.class, created.orderId())).isEqualTo("LATE_PAYMENT_RESERVATION_UNAVAILABLE");
        assertThat(reviewEventCount(created.orderId())).isEqualTo(1);

        assertThat(applyPaymentSuccess.apply(lateSuccess).outcome())
                .isEqualTo(PaymentSuccessResult.Outcome.REPLAYED);
        assertThat(reviewEventCount(created.orderId())).isEqualTo(1);
    }

    @Test
    void releaseResultAfterLateSuccessInFlightMovesSagaToManualReview() {
        CreateOrderFromAcceptedPurchaseCommand accepted = acceptedCommand();
        OrderCreationResult created = createOrder.create(accepted);
        UUID paymentId = UUID.randomUUID();
        PaymentFailedCommand failed = paymentFailed(created.orderId(), paymentId);
        assertThat(applyPaymentFailure.apply(failed).outcome())
                .isEqualTo(PaymentFailureResult.Outcome.APPLIED);

        PaymentSucceededCommand lateSuccess = lateSuccess(created.orderId(), paymentId);
        assertThat(applyPaymentSuccess.apply(lateSuccess).outcome())
                .isEqualTo(PaymentSuccessResult.Outcome.APPLIED);
        PurchaseReservationReleasedCommand released = released(created, accepted, failed);

        assertThat(applyReservationRelease.apply(released).outcome())
                .isEqualTo(PurchaseReservationReleaseResult.Outcome.MANUAL_REVIEW);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class,
                created.orderId())).isEqualTo("PENDING_PAYMENT");
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_sagas WHERE order_id = ?",
                String.class, created.orderId())).isEqualTo("MANUAL_REVIEW");
        assertThat(reviewEventCount(created.orderId())).isEqualTo(1);
        assertThat(applyReservationRelease.apply(released).outcome())
                .isEqualTo(PurchaseReservationReleaseResult.Outcome.REPLAYED);
        assertThat(reviewEventCount(created.orderId())).isEqualTo(1);
    }

    private long reviewEventCount(UUID orderId) {
        return jdbc.queryForObject("SELECT count(*) FROM order_outbox_events "
                        + "WHERE event_type = ? AND aggregate_id = ?", Long.class,
                "OrderPaymentReviewRequired", orderId);
    }

    private CreateOrderFromAcceptedPurchaseCommand acceptedCommand() {
        UUID purchaseRequestId = UUID.randomUUID();
        return new CreateOrderFromAcceptedPurchaseCommand(UUID.randomUUID(), "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1, UUID.randomUUID(), null,
                NOW, purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 2, new BigDecimal("10.0000"), "VND", NOW, NOW.plusSeconds(300),
                "flashsale.purchase.events.v1", 0, OFFSET.incrementAndGet(), null, null);
    }

    private PaymentFailedCommand paymentFailed(UUID orderId, UUID paymentId) {
        UUID eventId = UUID.randomUUID();
        return new PaymentFailedCommand(eventId, "PaymentFailed", 1, "payment-service", "PAYMENT",
                paymentId, 1, orderId, eventId, NOW.plusSeconds(1), paymentId, orderId,
                new BigDecimal("20.0000"), "VND", NOW.plusSeconds(1), "PROVIDER_TERMINAL_FAILURE",
                "stripe", "cs_test_failure", "flashsale.payment.events.v1", 0,
                OFFSET.incrementAndGet(), null, null, "a".repeat(64));
    }

    private PurchaseReservationReleasedCommand released(OrderCreationResult created,
            CreateOrderFromAcceptedPurchaseCommand accepted, PaymentFailedCommand failed) {
        return new PurchaseReservationReleasedCommand(UUID.randomUUID(), "PurchaseReservationReleased", 1,
                "flashsale-service", "PURCHASE_RESERVATION", accepted.reservationId(), 1,
                accepted.purchaseRequestId(), failed.eventId(), NOW.plusSeconds(2), accepted.purchaseRequestId(),
                created.orderId(), accepted.purchaseRequestId(), accepted.reservationId(), "RELEASED",
                "PROVIDER_TERMINAL_FAILURE", NOW.plusSeconds(2), "flashsale.purchase.events.v1", 0,
                OFFSET.incrementAndGet(), null, null, "b".repeat(64));
    }

    private PaymentSucceededCommand lateSuccess(UUID orderId, UUID paymentId) {
        return new PaymentSucceededCommand(UUID.randomUUID(), "PaymentSucceeded", 1, "payment-service", "PAYMENT",
                paymentId, 2, UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(3), paymentId, orderId,
                new BigDecimal("20.0000"), "VND", NOW.plusSeconds(3), "stripe", "cs_test_late_success", null,
                "flashsale.payment.events.v1", 0, OFFSET.incrementAndGet(), null, null, "c".repeat(64));
    }
}
