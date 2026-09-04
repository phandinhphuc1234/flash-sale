package com.philia.flashsale.inventory.regularhold;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.inventory.regularhold.application.command.ConfirmRegularStockHoldCommand;
import com.philia.flashsale.inventory.regularhold.application.port.in.ConfirmRegularStockHoldUseCase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the exact Order-only HTTP contract against PostgreSQL locks and additive migrations. */
@SpringBootTest(properties = "flashsale.inventory.regular-hold.api-enabled=true")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RegularStockHoldCompatibilityTests {
    private static final String AUDIENCE = "flash-sale-internal-api";
    private static final String SUBJECT = "order-service";
    private static final String SCOPE = "inventory.regular-hold.write";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("inventory_db")
            .withUsername("inventory")
            .withPassword("inventory");

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ConfirmRegularStockHoldUseCase confirmHold;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void clearData() {
        jdbc.execute("TRUNCATE TABLE stock_movements, regular_hold_command_inbox, regular_stock_hold_items, "
                + "regular_stock_holds, campaign_stock_allocations, outbox_events, inventory_items");
    }

    @Test
    void createsThenReplaysTheSameHoldWithExactOrderIdentity() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventory(variantId, 5);
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID shopperId = UUID.randomUUID();

        mockMvc.perform(holdRequest(holdId, purchaseRequestId, orderId, shopperId, variantId, 2)
                        .with(orderToken()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("HELD"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));

        mockMvc.perform(holdRequest(holdId, purchaseRequestId, orderId, shopperId, variantId, 2)
                        .with(orderToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.holdId").value(holdId.toString()));
    }

    @Test
    void rejectsPurchaseRequestReplayWhoseDurableIdentityDiffers() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventory(variantId, 5);
        UUID purchaseRequestId = UUID.randomUUID();
        mockMvc.perform(holdRequest(UUID.randomUUID(), purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(),
                        variantId, 1).with(orderToken()))
                .andExpect(status().isCreated());

        mockMvc.perform(holdRequest(UUID.randomUUID(), purchaseRequestId, UUID.randomUUID(), UUID.randomUUID(),
                        variantId, 1).with(orderToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("HOLD_IDENTITY_CONFLICT"));
    }

    @Test
    void rejectsAtomicRequestWhenAnyVariantIsInsufficient() throws Exception {
        UUID available = UUID.randomUUID();
        UUID insufficient = UUID.randomUUID();
        insertInventory(available, 4);
        insertInventory(insufficient, 1);

        mockMvc.perform(multiHoldRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        List.of(new Item(available, 1), new Item(insufficient, 2))).with(orderToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));

        assertThat(jdbc.queryForObject("select count(*) from regular_stock_holds", Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from regular_stock_hold_items", Long.class)).isZero();
    }

    @Test
    void rejectsUnknownVariantAndOutOfRangeRequestTime() throws Exception {
        UUID knownVariant = UUID.randomUUID();
        insertInventory(knownVariant, 5);

        mockMvc.perform(holdRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), 1).with(orderToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_ITEM_NOT_FOUND"));

        mockMvc.perform(multiHoldRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                        List.of(new Item(knownVariant, 1)), Instant.now().minusSeconds(91)).with(orderToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void concurrentDifferentRequestsCannotOversellOneLockedVariant() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventory(variantId, 5);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> concurrentRequest(ready, start, variantId));
            Future<MvcResult> second = executor.submit(() -> concurrentRequest(ready, start, variantId));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(first.get(20, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(20, TimeUnit.SECONDS).getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from regular_stock_hold_items", Long.class))
                    .isEqualTo(3L);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void confirmsPhysicalStockOnceAndDoesNotRepeatTheMovement() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventory(variantId, 5);
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID shopperId = UUID.randomUUID();
        mockMvc.perform(holdRequest(holdId, purchaseRequestId, orderId, shopperId, variantId, 2)
                        .with(orderToken()))
                .andExpect(status().isCreated());

        var initial = confirmHold.confirm(new ConfirmRegularStockHoldCommand(UUID.randomUUID(), holdId,
                purchaseRequestId, orderId, UUID.randomUUID(), Instant.now()));
        var replay = confirmHold.confirm(new ConfirmRegularStockHoldCommand(UUID.randomUUID(), holdId,
                purchaseRequestId, orderId, UUID.randomUUID(), Instant.now()));

        assertThat(initial.transitioned()).isTrue();
        assertThat(replay.transitioned()).isFalse();
        assertThat(jdbc.queryForObject("select on_hand_quantity from inventory_items where variant_id = ?", Long.class,
                variantId)).isEqualTo(3L);
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where reference_id = ?", Long.class,
                holdId)).isEqualTo(1L);
    }

    private MvcResult concurrentRequest(CountDownLatch ready, CountDownLatch start, UUID variantId) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent hold start gate timed out");
        }
        return mockMvc.perform(holdRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                variantId, 3).with(orderToken())).andReturn();
    }

    private MockHttpServletRequestBuilder holdRequest(UUID holdId, UUID purchaseRequestId, UUID orderId,
            UUID shopperId, UUID variantId, long quantity) {
        return multiHoldRequest(holdId, purchaseRequestId, orderId, shopperId, List.of(new Item(variantId, quantity)));
    }

    private MockHttpServletRequestBuilder multiHoldRequest(UUID holdId, UUID purchaseRequestId, UUID orderId,
            UUID shopperId, List<Item> items) {
        return multiHoldRequest(holdId, purchaseRequestId, orderId, shopperId, items, Instant.now());
    }

    private MockHttpServletRequestBuilder multiHoldRequest(UUID holdId, UUID purchaseRequestId, UUID orderId,
            UUID shopperId, List<Item> items, Instant requestedAt) {
        String body = """
                {"holdId":"%s","purchaseRequestId":"%s","orderId":"%s","shopperId":"%s","requestedAt":"%s","items":[%s]}
                """.formatted(holdId, purchaseRequestId, orderId, shopperId, requestedAt,
                items.stream().map(item -> "{\"variantId\":\"%s\",\"quantity\":%d}".formatted(item.variantId(), item.quantity()))
                        .reduce((left, right) -> left + "," + right).orElseThrow());
        return post("/internal/v1/regular-stock-holds").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Trace-Id", "trace-regular-hold");
    }

    private RequestPostProcessor orderToken() {
        return jwt().jwt(token -> token.subject(SUBJECT).audience(List.of(AUDIENCE)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + SCOPE));
    }

    private void insertInventory(UUID variantId, long onHandQuantity) {
        jdbc.update("""
                INSERT INTO inventory_items
                    (id, variant_id, sku_snapshot, on_hand_quantity, campaign_allocated_quantity, version)
                VALUES (?, ?, ?, ?, 0, 0)
                """, UUID.randomUUID(), variantId, "SKU-" + variantId.toString().substring(0, 8), onHandQuantity);
    }

    private record Item(UUID variantId, long quantity) { }
}
