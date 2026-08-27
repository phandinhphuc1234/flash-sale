package com.philia.flashsale.order.purchasesaga.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.order.order.application.command.CreateOrderFromAcceptedPurchaseCommand;
import com.philia.flashsale.order.order.application.port.in.CreateOrderFromAcceptedPurchaseUseCase;
import com.philia.flashsale.order.order.application.result.OrderCreationResult;
import com.philia.flashsale.order.purchasesaga.application.command.PaymentSucceededCommand;
import com.philia.flashsale.order.purchasesaga.application.exception.InvalidPaymentSuccessException;
import com.philia.flashsale.order.purchasesaga.application.port.in.ApplyPaymentSuccessUseCase;
import com.philia.flashsale.order.purchasesaga.application.result.PaymentSuccessResult;
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

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.outbox-publisher-enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.purchase-reservation-results-consumer-enabled=false",
        "order.runtime.payment-events-consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaymentSuccessTransitionIntegrationTests extends PostgreSqlIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final AtomicLong OFFSET = new AtomicLong(10_000);

    @Autowired private CreateOrderFromAcceptedPurchaseUseCase createOrder;
    @Autowired private ApplyPaymentSuccessUseCase applyPaymentSuccess;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void paymentSuccessAtomicallyStoresInboxSagaAndOneConfirmCommandAndReplayIsStable() {
        OrderCreationResult created = createOrder.create(accepted());
        UUID paymentId = UUID.randomUUID();
        PaymentSucceededCommand event = success(created.orderId(), paymentId, 2, "a".repeat(64),
                new BigDecimal("20.0000"));

        PaymentSuccessResult first = applyPaymentSuccess.apply(event);
        PaymentSuccessResult replay = applyPaymentSuccess.apply(event);

        assertThat(first.outcome()).isEqualTo(PaymentSuccessResult.Outcome.APPLIED);
        assertThat(replay.outcome()).isEqualTo(PaymentSuccessResult.Outcome.REPLAYED);
        assertThat(replay.confirmCommandId()).isEqualTo(first.confirmCommandId());
        assertThat(countSagaInboxRows(event.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_sagas WHERE order_id = ?",
                String.class, created.orderId())).isEqualTo("CONFIRMING_RESERVATION");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events "
                        + "WHERE aggregate_id = ? AND event_type = 'ConfirmPurchaseReservation'",
                Long.class, created.purchaseSagaId())).isEqualTo(1);
    }

    @Test
    void lowerPaymentVersionIsDurablyReceivedButCannotCreateAnotherConfirmCommand() {
        OrderCreationResult created = createOrder.create(accepted());
        UUID paymentId = UUID.randomUUID();
        assertThat(applyPaymentSuccess.apply(success(created.orderId(), paymentId, 2,
                "b".repeat(64), new BigDecimal("20.0000"))).outcome())
                .isEqualTo(PaymentSuccessResult.Outcome.APPLIED);
        PaymentSucceededCommand stale = success(created.orderId(), paymentId, 1,
                "c".repeat(64), new BigDecimal("20.0000"));

        assertThat(applyPaymentSuccess.apply(stale).outcome()).isEqualTo(PaymentSuccessResult.Outcome.STALE);
        assertThat(countSagaInboxRows(stale.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events "
                        + "WHERE aggregate_id = ? AND event_type = 'ConfirmPurchaseReservation'",
                Long.class, created.purchaseSagaId())).isEqualTo(1);
    }

    @Test
    void amountConflictRollsBackInboxSagaAndConfirmOutbox() {
        OrderCreationResult created = createOrder.create(accepted());
        PaymentSucceededCommand invalid = success(created.orderId(), UUID.randomUUID(), 1,
                "d".repeat(64), new BigDecimal("19.0000"));

        assertThatThrownBy(() -> applyPaymentSuccess.apply(invalid))
                .isInstanceOf(InvalidPaymentSuccessException.class)
                .hasMessageContaining("amount or currency");
        assertThat(countSagaInboxRows(invalid.eventId())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM purchase_sagas WHERE order_id = ?",
                String.class, created.orderId())).isEqualTo("PAYMENT_PENDING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_outbox_events "
                        + "WHERE aggregate_id = ? AND event_type = 'ConfirmPurchaseReservation'",
                Long.class, created.purchaseSagaId())).isZero();
    }

    private long countSagaInboxRows(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM purchase_saga_inbox WHERE event_id = ?",
                Long.class, eventId);
    }

    private CreateOrderFromAcceptedPurchaseCommand accepted() {
        UUID purchaseRequestId = UUID.randomUUID();
        return new CreateOrderFromAcceptedPurchaseCommand(UUID.randomUUID(), "PurchaseAccepted", 1,
                "flashsale-service", "PURCHASE_REQUEST", purchaseRequestId, 1, UUID.randomUUID(), null,
                NOW, purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), 2, new BigDecimal("10.0000"), "VND", NOW, NOW.plusSeconds(300),
                "flashsale.purchase.events.v1", 0, OFFSET.incrementAndGet(), null, null);
    }

    private PaymentSucceededCommand success(UUID orderId, UUID paymentId, long version,
            String fingerprint, BigDecimal amount) {
        return new PaymentSucceededCommand(UUID.randomUUID(), "PaymentSucceeded", 1, "payment-service",
                "PAYMENT", paymentId, version, orderId, UUID.randomUUID(), NOW.plusSeconds(version),
                paymentId, orderId, amount, "VND", NOW.plusSeconds(version), "stripe", "cs_test", null,
                "flashsale.payment.events.v1", 0, OFFSET.incrementAndGet(), null, null, fingerprint);
    }
}
