package com.philia.flashsale.campaign.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the Campaign admin boundary rejects invalid identity and requires its scope. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "flashsale.campaign.security.jwt.enabled=false")
@Import(CampaignAdminSecurityTests.TestJwtDecoderConfiguration.class)
class CampaignAdminSecurityTests {

    private static final String TRACE_ID = "campaign-admin-security-test";

    @MockBean
    private ClientRegistrationRepository clientRegistrations;

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("campaign_db")
                    .withUsername("campaign")
                    .withPassword("campaign");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/campaigns/{campaignId}", "00000000-0000-0000-0000-000000000001")
                        .header("X-Trace-Id", TRACE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("X-Trace-Id", TRACE_ID))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/campaigns/{campaignId}", "00000000-0000-0000-0000-000000000001")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void wrongAudienceTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/campaigns/{campaignId}", "00000000-0000-0000-0000-000000000001")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer wrong-audience-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void authenticatedTokenWithoutCampaignAdminScopeReturns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/campaigns/{campaignId}", "00000000-0000-0000-0000-000000000001")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer no-scope-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_ADMIN_REQUIRED"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    @Test
    void campaignAdminScopePassesAuthorization() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/api/v1/admin/campaigns")
                        .header("X-Trace-Id", TRACE_ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer campaign-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                // The request reaches controller validation, proving authorization succeeded.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("CAMPAIGN_VALIDATION_FAILED"));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestJwtDecoderConfiguration {

        @Bean("campaignPublicJwtDecoder")
        JwtDecoder campaignPublicJwtDecoder() {
            return this::decode;
        }

        @Bean("campaignInternalJwtDecoder")
        JwtDecoder campaignInternalJwtDecoder() {
            return this::decode;
        }

        private Jwt decode(String token) {
            if ("invalid-token".equals(token) || "wrong-audience-token".equals(token)) {
                throw new BadJwtException("test token rejected");
            }

            Instant now = Instant.now();
            Jwt.Builder builder = Jwt.withTokenValue(token)
                    .header("alg", "RS256")
                    .header("typ", "at+jwt")
                    .issuer("http://authentication-service:8080")
                    .audience(List.of("flash-sale-api"))
                    .issuedAt(now.minusSeconds(5))
                    .expiresAt(now.plusSeconds(300))
                    .subject("campaign-admin-test");
            if ("campaign-admin-token".equals(token)) {
                builder.claim("scope", "CAMPAIGN_ADMIN");
            }
            return builder.build();
        }
    }
}
