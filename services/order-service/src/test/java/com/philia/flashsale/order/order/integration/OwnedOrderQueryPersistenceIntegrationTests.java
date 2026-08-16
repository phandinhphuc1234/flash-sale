package com.philia.flashsale.order.order.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.order.order.application.port.out.ListOwnedOrdersPort;
import com.philia.flashsale.order.order.application.port.out.LoadOwnedOrderPort;
import com.philia.flashsale.order.order.application.query.GetOwnedOrderQuery;
import com.philia.flashsale.order.order.application.query.ListOwnedOrdersQuery;
import com.philia.flashsale.order.order.application.result.OrderDetailsResult;
import com.philia.flashsale.order.order.application.result.OrderPageResult;
import com.philia.flashsale.order.order.domain.model.OrderStatus;
import com.philia.flashsale.order.support.PostgreSqlIntegrationTestSupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/** Verifies owner scoping, deterministic ordering, and line mapping against PostgreSQL. */
@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "order.creation.enabled=false",
        "order.runtime.accepted-purchase-consumer-enabled=false",
        "order.runtime.outbox-publisher-enabled=false",
        "order.outbox.enabled=false"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OwnedOrderQueryPersistenceIntegrationTests extends PostgreSqlIntegrationTestSupport {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FOREIGN_OWNER = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final Instant CREATED = Instant.parse("2030-01-01T10:00:00Z");

    @Autowired
    private LoadOwnedOrderPort loadOwnedOrder;

    @Autowired
    private ListOwnedOrdersPort listOwnedOrders;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from order_lines");
        jdbc.update("delete from order_consumer_inbox");
        jdbc.update("delete from order_outbox_events");
        jdbc.update("delete from orders");
    }

    @Test
    void detailUsesOwnerPredicateAndMapsTheImmutableLineSnapshot() {
        UUID orderId = uuid(10);
        UUID lineId = uuid(20);
        UUID variantId = uuid(30);
        insertOrder(orderId, OWNER, "FS-010", CREATED);
        jdbc.update("""
                insert into order_lines
                    (id, order_id, variant_id, quantity, unit_price, line_amount, created_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, lineId, orderId, variantId, 2L, new BigDecimal("10.0000"),
                new BigDecimal("20.0000"), java.sql.Timestamp.from(CREATED));

        OrderDetailsResult details = loadOwnedOrder.load(new GetOwnedOrderQuery(orderId, OWNER)).orElseThrow();

        assertThat(details.id()).isEqualTo(orderId);
        assertThat(details.items()).singleElement().satisfies(item -> {
            assertThat(item.variantId()).isEqualTo(variantId);
            assertThat(item.quantity()).isEqualTo(2L);
            assertThat(item.lineAmount()).isEqualByComparingTo("20.0000");
        });
        assertThat(loadOwnedOrder.load(new GetOwnedOrderQuery(orderId, FOREIGN_OWNER))).isEmpty();
    }

    @Test
    void listContainsOnlyTheOwnerAndUsesCreatedAtThenIdDescending() {
        insertOrder(uuid(1), OWNER, "FS-001", CREATED);
        insertOrder(uuid(2), OWNER, "FS-002", CREATED);
        insertOrder(uuid(3), FOREIGN_OWNER, "FS-003", CREATED.plusSeconds(1));

        OrderPageResult page = listOwnedOrders.load(new ListOwnedOrdersQuery(OWNER, 0, 20));

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.orders()).extracting(order -> order.id()).containsExactly(uuid(2), uuid(1));
    }

    private void insertOrder(UUID id, UUID owner, String orderNumber, Instant createdAt) {
        jdbc.update("""
                insert into orders
                    (id, order_number, purchase_request_id, reservation_id, campaign_id, user_id,
                     status, currency, subtotal_amount, total_amount, accepted_at,
                     reservation_expires_at, row_version, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, orderNumber, uuid(id.getLeastSignificantBits() + 1000),
                uuid(id.getLeastSignificantBits() + 2000), uuid(id.getLeastSignificantBits() + 3000),
                owner, OrderStatus.PENDING_PAYMENT.name(), "VND", new BigDecimal("20.0000"),
                new BigDecimal("20.0000"), java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt.plusSeconds(300)), 0L,
                java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt));
    }

    private static UUID uuid(long value) {
        return new UUID(0L, value);
    }
}
