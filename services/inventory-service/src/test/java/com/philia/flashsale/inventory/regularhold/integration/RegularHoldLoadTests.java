package com.philia.flashsale.inventory.regularhold.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Bounded duplicate-load proof for one final unit in the Inventory regular-hold API. */
@SpringBootTest(properties = {
        "flashsale.inventory.regular-hold.api-enabled=true",
        "spring.datasource.hikari.maximum-pool-size=32",
        "spring.datasource.hikari.connection-timeout=30000"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class RegularHoldLoadTests {
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
    void oneHundredIdenticalHoldsCreateOneHoldAndNeverOversellTheFinalUnit() throws Exception {
        UUID variantId = UUID.randomUUID();
        UUID holdId = UUID.randomUUID();
        UUID purchaseRequestId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID shopperId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_items
                    (id, variant_id, sku_snapshot, on_hand_quantity, campaign_allocated_quantity, version)
                VALUES (?, ?, ?, 1, 0, 0)
                """, UUID.randomUUID(), variantId, "SKU-LOAD");

        String body = """
                {"holdId":"%s","purchaseRequestId":"%s","orderId":"%s","shopperId":"%s",
                 "requestedAt":"%s","items":[{"variantId":"%s","quantity":1}]}
                """.formatted(holdId, purchaseRequestId, orderId, shopperId, Instant.now(), variantId);
        ExecutorService executor = Executors.newFixedThreadPool(32);
        try {
            List<Callable<MvcResult>> calls = java.util.stream.IntStream.range(0, 100)
                    .<Callable<MvcResult>>mapToObj(index -> () -> mockMvc.perform(post(
                            "/internal/v1/regular-stock-holds").contentType(MediaType.APPLICATION_JSON)
                            .content(body).header("X-Trace-Id", "trace-regular-hold-load").with(orderToken()))
                            .andReturn()).toList();
            List<Future<MvcResult>> results = executor.invokeAll(calls);
            List<MvcResult> completed = results.stream().map(this::getResult).toList();
            List<Integer> statuses = completed.stream().map(result -> result.getResponse().getStatus()).toList();

            assertThat(statuses).containsOnly(200, 201);
            assertThat(statuses).contains(201);
            assertThat(statuses.stream().filter(status -> status == 201).count()).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from regular_stock_holds", Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select coalesce(sum(quantity), 0) from regular_stock_hold_items",
                    Long.class)).isEqualTo(1L);
            assertThat(jdbc.queryForObject("select on_hand_quantity from inventory_items where variant_id = ?",
                    Long.class, variantId)).isEqualTo(1L);
        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult getResult(Future<MvcResult> future) {
        try {
            return future.get(30, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError("regular hold load request failed", exception);
        }
    }

    private RequestPostProcessor orderToken() {
        return jwt().jwt(token -> token.subject(SUBJECT).audience(List.of(AUDIENCE)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + SCOPE));
    }
}
