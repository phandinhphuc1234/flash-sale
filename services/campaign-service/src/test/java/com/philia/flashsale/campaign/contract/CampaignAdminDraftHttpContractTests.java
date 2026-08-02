package com.philia.flashsale.campaign.contract;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * HTTP contract coverage for the four local Campaign draft endpoints.
 *
 * <p>The assertions intentionally describe the wire contract rather than controller internals:
 * Campaign success bodies use the shared envelope, mutations expose quoted versions, and the
 * effective trace ID is echoed in the header only.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(authorities = "SCOPE_CAMPAIGN_ADMIN")
class CampaignAdminDraftHttpContractTests {

    private static final String TRACE_ID = "campaign-draft-contract-trace";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
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
    void supportsCreateMetadataReplacementItemReplacementAndDetailWithSharedBodies() throws Exception {
        MvcResult create = mockMvc.perform(post("/api/v1/admin/campaigns")
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "flash-sale-contract",
                                  "name": "Contract campaign",
                                  "startAt": "2030-08-01T05:30:00Z",
                                  "endAt": "2030-08-01T07:30:00Z"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(header().string("ETag", "\"0\""))
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith(
                        "/api/v1/admin/campaigns/")))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").exists())
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andReturn();

        String location = create.getResponse().getHeader("Location");
        String campaignId = location.substring(location.lastIndexOf('/') + 1);
        UUID.fromString(campaignId);

        mockMvc.perform(patch("/api/v1/admin/campaigns/{campaignId}", campaignId)
                        .header("X-Trace-Id", TRACE_ID)
                        .header("If-Match", "\"0\"")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Updated contract campaign",
                                  "startAt": "2030-08-01T06:00:00Z",
                                  "endAt": "2030-08-01T08:00:00Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(header().string("ETag", "\"1\""))
                .andExpect(jsonPath("$.data.id").value(campaignId))
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        String variantId = UUID.randomUUID().toString();
        mockMvc.perform(put("/api/v1/admin/campaigns/{campaignId}/item", campaignId)
                        .header("X-Trace-Id", TRACE_ID)
                        .header("If-Match", "\"1\"")
                        .contentType("application/json")
                        .content("""
                                {
                                  "variantId": "%s",
                                  "campaignPrice": 19900000.0000,
                                  "requestedQuantity": 1000,
                                  "purchaseLimitPerUser": 1
                                }
                                """.formatted(variantId)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.data.item.variantId").value(variantId))
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/campaigns/{campaignId}", campaignId)
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.data.id").value(campaignId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.item.variantId").value(variantId))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void returnsValidationErrorWithTraceAndFieldErrorsForInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/admin/campaigns")
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "code": "",
                                  "name": "",
                                  "startAt": "2030-08-01T08:00:00Z",
                                  "endAt": "2030-08-01T07:30:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").doesNotExist())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void rejectsMissingIfMatchWithTheCampaignValidationContract() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/campaigns/{campaignId}", UUID.randomUUID())
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Updated contract campaign",
                                  "startAt": "2030-08-01T06:00:00Z",
                                  "endAt": "2030-08-01T08:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }
}
