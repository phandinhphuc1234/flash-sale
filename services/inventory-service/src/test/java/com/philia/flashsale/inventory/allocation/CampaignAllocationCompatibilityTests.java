package com.philia.flashsale.inventory.allocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Compatibility tests for Campaign's narrow Inventory allocation contract.
 *
 * <p>The idempotency and shared-envelope assertions exercise the existing endpoint. The narrow
 * subject/audience/scope assertions intentionally remain red until T051-T052 add the dedicated
 * internal security chain and stable allocation error codes.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CampaignAllocationCompatibilityTests {

    private static final String INTERNAL_AUDIENCE = "flash-sale-internal-api";
    private static final String PUBLIC_AUDIENCE = "flash-sale-api";
    private static final String CAMPAIGN_SUBJECT = "campaign-service";
    private static final String NARROW_SCOPE = "inventory.campaign.allocate";
    private static final String BROAD_SCOPE = "INVENTORY_WRITE";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("inventory_db")
            .withUsername("inventory")
            .withPassword("inventory");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void clearBusinessData() {
        jdbc.execute("""
                TRUNCATE TABLE
                    outbox_events,
                    stock_movements,
                    campaign_stock_allocations,
                    inventory_items
                """);
    }

    @Test
    void campaignServiceNarrowScopeReturnsExistingSuccessEnvelope() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventoryItem(variantId, 100);
        UUID requestId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        mockMvc.perform(allocationRequest(requestId, campaignId, variantId, 10)
                        .with(jwt().jwt(token -> token.subject(CAMPAIGN_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_" + NARROW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("Allocation accepted"))
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.data.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.data.variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.data.allocatedQuantity").value(10))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void broadInventoryWriteScopeCannotAuthorizeCampaignAllocation() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventoryItem(variantId, 100);

        mockMvc.perform(allocationRequest(UUID.randomUUID(), UUID.randomUUID(), variantId, 10)
                        .with(jwt().jwt(token -> token.subject(CAMPAIGN_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                        .authorities(new SimpleGrantedAuthority("SCOPE_" + BROAD_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicAdministratorTokenCannotBeSubstitutedForCampaignAllocation() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventoryItem(variantId, 100);

        mockMvc.perform(allocationRequest(UUID.randomUUID(), UUID.randomUUID(), variantId, 10)
                        .with(jwt().jwt(token -> token.subject("administrator-1")
                                .audience(List.of(PUBLIC_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_" + BROAD_SCOPE))))
                .andExpect(status().isForbidden());
    }

    @Test
    void insufficientStockUsesStableMachineReadableErrorCode() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventoryItem(variantId, 2);

        mockMvc.perform(allocationRequest(UUID.randomUUID(), UUID.randomUUID(), variantId, 10)
                        .with(jwt().jwt(token -> token.subject(CAMPAIGN_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_" + NARROW_SCOPE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_INSUFFICIENT_STOCK"));
    }

    @Test
    void requestIdReuseWithDifferentPayloadUsesStableConflictCode() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventoryItem(variantId, 100);
        UUID requestId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        var narrowToken = jwt().jwt(token -> token.subject(CAMPAIGN_SUBJECT)
                .audience(List.of(INTERNAL_AUDIENCE)))
                .authorities(new SimpleGrantedAuthority("SCOPE_" + NARROW_SCOPE));

        mockMvc.perform(allocationRequest(requestId, campaignId, variantId, 10).with(narrowToken))
                .andExpect(status().isOk());

        mockMvc.perform(allocationRequest(requestId, campaignId, variantId, 20)
                        .with(jwt().jwt(token -> token.subject(CAMPAIGN_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_" + NARROW_SCOPE))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVENTORY_ALLOCATION_REQUEST_CONFLICT"));
    }

    @Test
    void sameRequestIdAndPayloadReplaysTheSameAllocation() throws Exception {
        UUID variantId = UUID.randomUUID();
        insertInventoryItem(variantId, 100);
        UUID requestId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        MvcResult first = mockMvc.perform(allocationRequest(requestId, campaignId, variantId, 10)
                        .with(jwt().jwt(token -> token.subject(CAMPAIGN_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_" + NARROW_SCOPE))))
                .andExpect(status().isOk())
                .andReturn();
        String firstId = allocationId(first);

        MvcResult replay = mockMvc.perform(allocationRequest(requestId, campaignId, variantId, 10)
                        .with(jwt().jwt(token -> token.subject(CAMPAIGN_SUBJECT)
                                .audience(List.of(INTERNAL_AUDIENCE)))
                                .authorities(new SimpleGrantedAuthority("SCOPE_" + NARROW_SCOPE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value(requestId.toString()))
                .andReturn();

        String replayId = allocationId(replay);
        assertThat(replayId).isEqualTo(firstId);
    }

    private String allocationId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
    }

    private MockHttpServletRequestBuilder allocationRequest(
            UUID requestId, UUID campaignId, UUID variantId, long quantity) {
        return post("/internal/v1/campaign-stock-allocations")
                .header("X-Trace-Id", "trace-inventory-allocation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "requestId": "%s",
                          "campaignId": "%s",
                          "variantId": "%s",
                          "quantity": %d,
                          "reason": "CAMPAIGN_SCHEDULE"
                        }
                        """.formatted(requestId, campaignId, variantId, quantity));
    }

    private void insertInventoryItem(UUID variantId, long onHandQuantity) {
        jdbc.update("""
                INSERT INTO inventory_items
                    (id, variant_id, sku_snapshot, on_hand_quantity, campaign_allocated_quantity, version)
                VALUES (?, ?, ?, ?, 0, 0)
                """, UUID.randomUUID(), variantId, "SKU-" + variantId.toString().substring(0, 8), onHandQuantity);
    }
}
