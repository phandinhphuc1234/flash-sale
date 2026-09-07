package com.philia.flashsale.order.regularpurchase.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.order.domain.model.Order;
import com.philia.flashsale.order.order.domain.model.OrderLine;
import com.philia.flashsale.order.order.domain.model.PurchaseSource;
import com.philia.flashsale.order.order.domain.valueobject.Money;
import com.philia.flashsale.order.purchasesaga.domain.model.PurchaseSaga;
import com.philia.flashsale.order.regularpurchase.adapter.out.persistence.jpa.RegularPurchasePersistenceAdapter;
import com.philia.flashsale.order.regularpurchase.application.model.RegularPurchaseAcceptance;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseLine;
import com.philia.flashsale.order.regularpurchase.domain.model.RegularPurchaseRequest;
import com.philia.flashsale.order.support.PostgreSqlIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** Proves the regular accepted commit cannot leave Order, Saga, or either required outbox intent behind. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.kafka.bootstrap-servers=localhost:19092",
        "order.runtime.outbox-publisher-enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RegularPurchasePersistenceIntegrationTests extends PostgreSqlIntegrationTestSupport {

    private static final Instant ACCEPTED_AT = Instant.parse("2032-01-01T10:00:00Z");

    @Autowired
    private RegularPurchasePersistenceAdapter persistence;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void acceptedBuyNowCommitsIntakeOrderLinesSagaAndBothOutboxIntentsTogether() {
        UUID shopperId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        RegularPurchaseRequest registered = RegularPurchaseRequest.receiveBuyNow(requestId, shopperId, "buy-now-1",
                orderId, holdId, line(UUID.randomUUID(), 2, "179000.0000"), ACCEPTED_AT);
        assertThat(persistence.register(registered)).isEqualTo(registered);

        Instant holdExpiry = ACCEPTED_AT.plusSeconds(300);
        RegularPurchaseRequest accepted = registered.productValidated(ACCEPTED_AT.plusSeconds(1))
                .holdAcquired(holdExpiry, ACCEPTED_AT.plusSeconds(2))
                .accept(orderId, ACCEPTED_AT.plusSeconds(3));
        Order order = Order.regular(orderId, "REG-2032-000001", requestId, PurchaseSource.BUY_NOW, holdId,
                shopperId, "VND", List.of(OrderLine.create(UUID.randomUUID(), registered.lines().getFirst().variantId(),
                        2, Money.of(new BigDecimal("179000.0000")))), null, null, ACCEPTED_AT.plusSeconds(3), holdExpiry);
        PurchaseSaga saga = PurchaseSaga.startRegular(orderId, requestId, holdId, holdExpiry, ACCEPTED_AT.plusSeconds(3));
        UUID createdEventId = UUID.randomUUID();
        UUID paymentEventId = UUID.randomUUID();

        assertThat(persistence.accept(new RegularPurchaseAcceptance(accepted, order, saga, createdEventId,
                paymentEventId, requestId, requestId, ACCEPTED_AT.plusSeconds(3), null, null)))
                .isEqualTo(accepted);

        assertThat(count("regular_purchase_requests", "id", requestId)).isEqualTo(1);
        assertThat(count("orders", "id", orderId)).isEqualTo(1);
        assertThat(count("order_lines", "order_id", orderId)).isEqualTo(1);
        assertThat(count("purchase_sagas", "id", requestId)).isEqualTo(1);
        assertThat(count("order_outbox_events", "event_id", createdEventId)).isEqualTo(1);
        assertThat(count("order_outbox_events", "event_id", paymentEventId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select purchase_source from orders where id = ?", String.class, orderId))
                .isEqualTo("BUY_NOW");
        assertThat(jdbc.queryForObject("select stock_participant_type from purchase_sagas where id = ?", String.class,
                requestId)).isEqualTo("REGULAR_STOCK_HOLD");
        assertThat(jdbc.queryForObject("select event_type from order_outbox_events where event_id = ?", String.class,
                createdEventId)).isEqualTo("OrderCreatedV2");
        assertThat(jdbc.queryForObject("select event_version from order_outbox_events where event_id = ?", Integer.class,
                createdEventId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select event_type from order_outbox_events where event_id = ?", String.class,
                paymentEventId)).isEqualTo("PaymentRequested");
    }

    @Test
    void sameShopperKeyReturnsTheExistingIntakeBeforeAnyDownstreamCall() {
        UUID shopperId = UUID.randomUUID();
        RegularPurchaseRequest first = RegularPurchaseRequest.receiveBuyNow(UUID.randomUUID(), shopperId, "same-key",
                UUID.randomUUID(), UUID.randomUUID(), line(UUID.randomUUID(), 1, "1.0000"), ACCEPTED_AT);
        RegularPurchaseRequest conflictingBody = RegularPurchaseRequest.receiveBuyNow(UUID.randomUUID(), shopperId,
                "same-key", UUID.randomUUID(), UUID.randomUUID(), line(UUID.randomUUID(), 2, "1.0000"), ACCEPTED_AT);

        persistence.register(first);
        RegularPurchaseRequest replayBoundary = persistence.register(conflictingBody);

        assertThat(replayBoundary.id()).isEqualTo(first.id());
        assertThat(replayBoundary.requestFingerprint()).isEqualTo(first.requestFingerprint());
        assertThat(count("regular_purchase_requests", "shopper_id", shopperId)).isEqualTo(1);
    }

    @Test
    void staleIntakeIsClaimedOnceUntilTheLeaseIsReleasedOrExpires() {
        UUID requestId = UUID.randomUUID();
        RegularPurchaseRequest request = RegularPurchaseRequest.receiveBuyNow(requestId, UUID.randomUUID(),
                "lease-key", UUID.randomUUID(), UUID.randomUUID(), line(UUID.randomUUID(), 1, "1.0000"),
                ACCEPTED_AT.minusSeconds(60));
        persistence.register(request);
        Instant recoveryNow = ACCEPTED_AT.plusSeconds(60);

        var first = persistence.claim("worker-a", recoveryNow, 1, Duration.ofSeconds(30));
        var second = persistence.claim("worker-b", recoveryNow, 1, Duration.ofSeconds(30));

        assertThat(first).hasSize(1);
        assertThat(first.getFirst().request().id()).isEqualTo(requestId);
        assertThat(second).noneMatch(claim -> claim.request().id().equals(requestId));
        assertThat(jdbc.queryForObject("select recovery_lease_owner from regular_purchase_requests where id = ?",
                String.class, requestId)).isEqualTo("worker-a");

        persistence.release(requestId, "worker-a");
        assertThat(persistence.claim("worker-b", recoveryNow, 10, Duration.ofSeconds(30)))
                .anyMatch(claim -> claim.request().id().equals(requestId));
    }

    private static RegularPurchaseLine line(UUID variantId, long quantity, String price) {
        return new RegularPurchaseLine(variantId, quantity, Money.of(new BigDecimal(price)), "VND", null);
    }

    private long count(String table, String column, UUID id) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + column + " = ?", Long.class, id);
    }
}
