package com.philia.flashsale.campaign.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.philia.flashsale.campaign.campaign.application.port.out.CampaignActorPort;
import com.philia.flashsale.campaign.campaign.application.port.out.CampaignClockPort;
import com.philia.flashsale.campaign.campaign.adapter.in.scheduling.CampaignLifecycleScheduler;
import java.math.BigDecimal;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** HTTP contract for authorized manual lifecycle recovery activation. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(authorities = "SCOPE_CAMPAIGN_ADMIN")
class CampaignManualActivationHttpContractTests {

    private static final Instant NOW = Instant.parse("2030-08-01T10:00:00Z");
    private static final String TRACE_ID = "manual-activation-contract-trace";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campaign_activation_contract_db")
            .withUsername("campaign")
            .withPassword("campaign");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockBean
    private CampaignClockPort clock;

    @MockBean
    private CampaignActorPort actor;

    @MockBean
    private CampaignLifecycleScheduler scheduler;

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
    void reset() {
        jdbc.execute("TRUNCATE TABLE campaign_outbox_events, campaign_schedule_operations, "
                + "campaign_items, campaigns");
        when(clock.now()).thenReturn(NOW);
        when(actor.currentActor()).thenReturn("campaign-admin");
    }

    @Test
    void activatesADueCampaignWithIfMatchAndReturnsNextEtag() throws Exception {
        UUID campaignId = insertScheduled(NOW.minusSeconds(1), NOW.plusSeconds(3600), 1);

        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/activate", campaignId)
                        .header("If-Match", "\"1\"")
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(header().string("ETag", "\"2\""))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void rejectsEarlyActivationAndKeepsScheduledState() throws Exception {
        UUID campaignId = insertScheduled(NOW.plusSeconds(60), NOW.plusSeconds(3600), 1);

        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/activate", campaignId)
                        .header("If-Match", "\"1\"")
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_INVALID_STATUS"));

        assertStatus(campaignId, "SCHEDULED");
    }

    @Test
    void requiresIfMatchAndCampaignAdminAuthority() throws Exception {
        UUID campaignId = insertScheduled(NOW.minusSeconds(1), NOW.plusSeconds(3600), 1);

        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/activate", campaignId)
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Trace-Id", TRACE_ID));

        mockMvc.perform(post("/api/v1/admin/campaigns/{campaignId}/activate", campaignId)
                        .with(SecurityMockMvcRequestPostProcessors.user("user")
                                .authorities(new SimpleGrantedAuthority("SCOPE_OTHER")))
                        .header("If-Match", "\"1\"")
                        .header("X-Trace-Id", TRACE_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private UUID insertScheduled(Instant start, Instant end, long version) {
        UUID campaignId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID allocationId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO campaigns (id, code, name, status, start_at, end_at, scheduled_at,
                    version, created_by, updated_by, created_at, updated_at)
                VALUES (?, ?, 'Activation campaign', 'SCHEDULED', ?, ?, ?, ?, 'admin', 'admin', ?, ?)
                """, campaignId, "ACTIVATION-" + campaignId.toString().substring(0, 8).toUpperCase(),
                Timestamp.from(start), Timestamp.from(end), Timestamp.from(start.minusSeconds(1)), version,
                Timestamp.from(start.minusSeconds(60)), Timestamp.from(start.minusSeconds(60)));
        jdbc.update("""
                INSERT INTO campaign_items (id, campaign_id, product_id, variant_id,
                    inventory_allocation_id, variant_sku_snapshot, base_price_snapshot, currency_snapshot,
                    campaign_price, requested_quantity, allocated_quantity, purchase_limit_per_user,
                    created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'SKU-ACTIVE', ?, 'VND', ?, 10, 10, 1, ?, ?)
                """, UUID.randomUUID(), campaignId, productId, variantId, allocationId,
                new BigDecimal("100.00"), new BigDecimal("90.00"),
                Timestamp.from(start.minusSeconds(60)), Timestamp.from(start.minusSeconds(60)));
        return campaignId;
    }

    private void assertStatus(UUID campaignId, String expected) {
        assertThat(jdbc.queryForObject(
                "SELECT status FROM campaigns WHERE id = ?", String.class, campaignId)).isEqualTo(expected);
    }
}
