package com.philia.flashsale.campaign.contract;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Contract and security tests for the private Flash Sale Campaign snapshot endpoint. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "flashsale.campaign.security.jwt.enabled=false",
        "spring.security.oauth2.client.provider.authentication.token-uri=http://authentication-service:8080/oauth2/token",
        "spring.security.oauth2.client.registration.campaign-product.provider=authentication",
        "spring.security.oauth2.client.registration.campaign-product.client-id=campaign-service",
        "spring.security.oauth2.client.registration.campaign-product.client-secret=test-secret",
        "spring.security.oauth2.client.registration.campaign-product.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.registration.campaign-product.scope=catalog.read",
        "spring.security.oauth2.client.registration.campaign-inventory-allocation.provider=authentication",
        "spring.security.oauth2.client.registration.campaign-inventory-allocation.client-id=campaign-service",
        "spring.security.oauth2.client.registration.campaign-inventory-allocation.client-secret=test-secret",
        "spring.security.oauth2.client.registration.campaign-inventory-allocation.authorization-grant-type=client_credentials",
        "spring.security.oauth2.client.registration.campaign-inventory-allocation.scope=inventory.campaign.allocate"
})
@Import(InternalCampaignSnapshotContractTests.TestJwtDecoderConfiguration.class)
class InternalCampaignSnapshotContractTests {

    private static final String TRACE_ID = "snapshot-contract-test";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campaign_snapshot_contract_db")
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
    void resetData() {
        jdbc.update("DELETE FROM campaign_items");
        jdbc.update("DELETE FROM campaigns");
    }

    @Test
    void flashSaleServiceReceivesDirectSnapshotWithoutApiEnvelope() throws Exception {
        UUID campaignId = insertCampaign("SCHEDULED");

        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer flashsale-token")
                        .header("X-Trace-Id", TRACE_ID)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(jsonPath("$.campaignId").value(campaignId.toString()))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.item.variantSku").value("SKU-1"))
                .andExpect(jsonPath("$.item.allocatedQuantity").value(10))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void activeAndEndedCampaignsExposeTheSameStableSnapshotShape() throws Exception {
        UUID activeId = insertCampaign("ACTIVE");
        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", activeId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer flashsale-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.item.inventoryAllocationId").exists());

        resetData();
        UUID endedId = insertCampaign("ENDED");
        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", endedId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer flashsale-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"))
                .andExpect(jsonPath("$.item.variantSku").value("SKU-1"));
    }

    @Test
    void draftSnapshotReturnsTheApprovedNotFoundContract() throws Exception {
        UUID campaignId = insertCampaign("DRAFT");

        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer flashsale-token")
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void missingOrIncompleteSnapshotReturnsTheSameNotFoundContract() throws Exception {
        UUID incompleteId = insertCampaign("SCHEDULED", false);

        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", incompleteId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer flashsale-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_SNAPSHOT_NOT_FOUND"));

        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer flashsale-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_SNAPSHOT_NOT_FOUND"));
    }

    @Test
    void wrongSubjectAndMissingScopeCannotReadSnapshot() throws Exception {
        UUID campaignId = insertCampaign("SCHEDULED");

        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-subject-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_ACCESS_DENIED"));

        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer no-scope-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_ACCESS_DENIED"));
    }

    @Test
    void missingTokenReturns401AndAdminTokenCannotSubstitute() throws Exception {
        UUID campaignId = insertCampaign("SCHEDULED");

        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", campaignId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/internal/v1/campaigns/{campaignId}/snapshot", campaignId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer admin-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_ACCESS_DENIED"));
    }

    private UUID insertCampaign(String status) {
        return insertCampaign(status, true);
    }

    private UUID insertCampaign(String status, boolean completeItem) {
        UUID campaignId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.of(2030, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2030, 1, 1, 1, 0, 0, 0, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO campaigns (
                    id, code, name, status, start_at, end_at, scheduled_at,
                    activated_at, ended_at, version, created_by, updated_by, created_at, updated_at
                ) VALUES (?, ?, 'Campaign', ?, ?, ?, ?, ?, ?, 3, 'admin', 'admin', ?, ?)
                """, campaignId, ("CAMPAIGN-" + campaignId).toUpperCase(Locale.ROOT), status, start, end,
                "SCHEDULED".equals(status) ? start : null,
                "ACTIVE".equals(status) ? start : null,
                "ENDED".equals(status) ? end : null,
                start, start);
        if (completeItem && !"DRAFT".equals(status)) {
            jdbc.update("""
                    INSERT INTO campaign_items (
                        id, campaign_id, product_id, variant_id, inventory_allocation_id,
                        variant_sku_snapshot, base_price_snapshot, currency_snapshot, campaign_price,
                        requested_quantity, allocated_quantity, purchase_limit_per_user, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'SKU-1', 1000.0000, 'VND', 900.0000, 10, 10, 1, ?, ?)
                    """, UUID.randomUUID(), campaignId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    start, start);
        }
        return campaignId;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJwtDecoderConfiguration {

        @Bean("campaignPublicJwtDecoder")
        JwtDecoder publicDecoder() {
            return this::decode;
        }

        @Bean("campaignInternalJwtDecoder")
        JwtDecoder internalDecoder() {
            return this::decode;
        }

        private Jwt decode(String token) {
            if ("invalid-token".equals(token)) {
                throw new BadJwtException("test token rejected");
            }
            Instant now = Instant.now();
            String subject = "flashsale-service";
            List<String> audience = List.of("flash-sale-internal-api");
            List<String> scopes = List.of("campaign.snapshot.read");
            if ("wrong-subject-token".equals(token)) {
                subject = "campaign-service";
            } else if ("no-scope-token".equals(token)) {
                scopes = List.of();
            } else if ("admin-token".equals(token)) {
                subject = "admin-user";
                audience = List.of("flash-sale-api");
                scopes = List.of("CAMPAIGN_ADMIN");
            }
            return Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .header("typ", "at+jwt")
                    .issuer("http://authentication-service:8080")
                    .audience(audience)
                    .issuedAt(now.minusSeconds(5))
                    .expiresAt(now.plusSeconds(300))
                    .subject(subject)
                    .claim("scope", String.join(" ", scopes))
                    .build();
        }
    }
}
