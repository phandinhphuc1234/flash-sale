package com.philia.flashsale.inventory.regularhold.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Proves that overlapping multi-item regular holds are all-or-nothing. */
@SpringBootTest(properties = "flashsale.inventory.regular-hold.api-enabled=true")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class MultiItemRegularHoldConcurrencyTests {
    private static final String AUDIENCE = "flash-sale-internal-api";
    private static final String SUBJECT = "order-service";
    private static final String SCOPE = "inventory.regular-hold.write";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("inventory_db").withUsername("inventory").withPassword("inventory");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;

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
    void overlappingMultiItemRequestsHaveOneWinnerAndNoPartialHold() throws Exception {
        UUID firstVariant = UUID.randomUUID();
        UUID secondVariant = UUID.randomUUID();
        insertInventory(firstVariant, 5);
        insertInventory(secondVariant, 5);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> concurrentRequest(ready, start, firstVariant, secondVariant));
            Future<MvcResult> second = executor.submit(() -> concurrentRequest(ready, start, firstVariant, secondVariant));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> statuses = List.of(first.get(20, TimeUnit.SECONDS).getResponse().getStatus(),
                    second.get(20, TimeUnit.SECONDS).getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(201, 409);
            assertThat(jdbc.queryForObject("select count(*) from regular_stock_holds", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from regular_stock_hold_items", Long.class)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from regular_stock_hold_items "
                    + "where variant_id = ?", Long.class, firstVariant)).isEqualTo(3L);
            assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from regular_stock_hold_items "
                    + "where variant_id = ?", Long.class, secondVariant)).isEqualTo(3L);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private MvcResult concurrentRequest(CountDownLatch ready, CountDownLatch start,
            UUID firstVariant, UUID secondVariant) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start gate timed out");
        return mockMvc.perform(request(firstVariant, secondVariant).with(orderToken())).andReturn();
    }

    private MockHttpServletRequestBuilder request(UUID firstVariant, UUID secondVariant) {
        String body = """
                {"holdId":"%s","purchaseRequestId":"%s","orderId":"%s","shopperId":"%s",\
                "requestedAt":"%s","items":[{"variantId":"%s","quantity":3},{"variantId":"%s","quantity":3}]}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                java.time.Instant.now(), firstVariant, secondVariant);
        return post("/internal/v1/regular-stock-holds").contentType(MediaType.APPLICATION_JSON).content(body)
                .header("X-Trace-Id", "trace-multi-item-concurrency");
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
}
