package com.philia.flashsale.campaign.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
    }

    @Test
    void scheduleRequiresIfMatchAndIdempotencyKeyAndAcceptsEmptyJsonObject() throws Exception {
        UUID campaignId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/schedule", campaignId)
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", TRACE_ID));

        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/schedule", campaignId)
                        .header("X-Trace-Id", TRACE_ID)
                        .header("If-Match", "\"0\"")
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
}
