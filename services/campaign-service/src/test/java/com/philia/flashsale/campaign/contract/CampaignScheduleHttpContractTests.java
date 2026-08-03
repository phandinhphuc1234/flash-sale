package com.philia.flashsale.campaign.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.philia.flashsale.campaign.campaign.application.command.AllocateCampaignInventoryCommand;
import com.philia.flashsale.campaign.campaign.application.port.out.AllocateCampaignInventoryPort;
import com.philia.flashsale.campaign.campaign.application.port.out.ValidateCampaignVariantPort;
import com.philia.flashsale.campaign.campaign.application.result.CampaignInventoryAllocation;
import com.philia.flashsale.campaign.campaign.application.result.ValidatedCampaignVariant;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Test-first HTTP contract for the Campaign schedule command. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(authorities = "SCOPE_CAMPAIGN_ADMIN")
class CampaignScheduleHttpContractTests {

    private static final String TRACE_ID = "campaign-schedule-contract-trace";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campaign_db")
            .withUsername("campaign")
            .withPassword("campaign");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private ValidateCampaignVariantPort productPort;

    @MockBean
    private AllocateCampaignInventoryPort inventoryPort;

    @MockBean
    private ClientRegistrationRepository clientRegistrations;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void clearCampaignData() {
        jdbc.execute("TRUNCATE TABLE campaign_outbox_events, campaign_schedule_operations, "
                + "campaign_items, campaigns");
        when(productPort.validate(any(), anyString())).thenAnswer(invocation -> {
            UUID variantId = invocation.getArgument(0);
            return new ValidatedCampaignVariant(
                    UUID.randomUUID(), variantId, "SKU-TEST", "ACTIVE", "ACTIVE", true,
                    new BigDecimal("100.00"), "VND");
        });
        when(inventoryPort.allocate(any(AllocateCampaignInventoryCommand.class), anyString()))
                .thenAnswer(invocation -> {
                    AllocateCampaignInventoryCommand command = invocation.getArgument(0);
                    return new CampaignInventoryAllocation(
                            UUID.randomUUID(), command.requestId(), command.campaignId(), command.variantId(),
                            command.quantity(), 0, 0, "ALLOCATED");
                });
    }

    @Test
    void scheduleRequiresIfMatchAndIdempotencyKeyAndAcceptsEmptyJsonObject() throws Exception {
        UUID campaignId = UUID.randomUUID();
        seedDraft(campaignId, 2);

        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/schedule", campaignId)
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", TRACE_ID));

        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/schedule", campaignId)
                        .header("X-Trace-Id", TRACE_ID)
                        .header("If-Match", "\"2\"")
                        .header("Idempotency-Key", "schedule-contract-1")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(header().string("ETag", "\"3\""));
    }

    @Test
    void sameIdempotencyKeyReplaysTheSameScheduleResultAndEtag() throws Exception {
        UUID campaignId = UUID.randomUUID();
        seedDraft(campaignId, 2);
        var request = post("/api/v1/admin/campaigns/{campaignId}/schedule", campaignId)
                .header("X-Trace-Id", TRACE_ID)
                .header("If-Match", "\"2\"")
                .header("Idempotency-Key", "schedule-replay-1")
                .contentType("application/json")
                .content("{}");

        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"3\""))
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));
    }

    @Test
    void approvedScheduleErrorsUseStableCodesAndTraceHeader() throws Exception {
        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/schedule", UUID.randomUUID())
                        .header("X-Trace-Id", TRACE_ID)
                        .header("If-Match", "\"0\"")
                        .header("Idempotency-Key", "schedule-error-1")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_NOT_FOUND"));
    }

    private void seedDraft(UUID campaignId, long version) {
        UUID variantId = UUID.randomUUID();
        Instant now = Instant.now();
        jdbc.update("INSERT INTO campaigns (id, code, name, status, start_at, end_at, version, "
                        + "created_by, updated_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                campaignId, "CONTRACT-" + campaignId.toString().substring(0, 8).toUpperCase(),
                "Contract campaign", "DRAFT", Timestamp.from(now.plusSeconds(3600)),
                Timestamp.from(now.plusSeconds(7200)), version, "test-admin", "test-admin",
                Timestamp.from(now), Timestamp.from(now));
        jdbc.update("INSERT INTO campaign_items (id, campaign_id, variant_id, campaign_price, "
                        + "requested_quantity, purchase_limit_per_user, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), campaignId, variantId, new BigDecimal("90.00"), 10L, 1L,
                Timestamp.from(now), Timestamp.from(now));
    }
}
