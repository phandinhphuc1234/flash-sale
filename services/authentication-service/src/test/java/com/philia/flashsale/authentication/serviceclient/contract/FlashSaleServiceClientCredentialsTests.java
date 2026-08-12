package com.philia.flashsale.authentication.serviceclient.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Proves the dedicated Flash Sale client cannot use Campaign service scopes. */
@SpringBootTest(properties = {
        "flashsale.authentication.http.enabled=true",
        "flashsale.authentication.core-enabled=true",
        "flashsale.authentication.runtime-enabled=true",
        "spring.liquibase.enabled=true"
})
@AutoConfigureMockMvc
@Testcontainers
class FlashSaleServiceClientCredentialsTests {

    private static final String FLASHSALE_ID = "flashsale-service";
    private static final String FLASHSALE_SECRET = "flashsale-secret";
    private static final String CAMPAIGN_ID = "campaign-service";
    private static final String ISSUER = "http://authentication-service:8080";
    private static final KeyPair KEY_PAIR = createKeyPair();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("flashsale_client_contract_db")
            .withUsername("auth")
            .withPassword("auth");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("flashsale.auth.jwt.issuer", () -> ISSUER);
        registry.add("flashsale.auth.jwt.audience", () -> "flash-sale-api");
        registry.add("flashsale.auth.jwt.key-id", () -> "b3-test-key");
        registry.add("flashsale.auth.jwt.public-key-pem", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
        registry.add("flashsale.auth.jwt.private-key-pem", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
    }

    @BeforeEach
    void resetClients() {
        jdbc.update("TRUNCATE TABLE oauth_client_scopes, oauth_clients");
        insertClient(FLASHSALE_ID, FLASHSALE_SECRET, "ACTIVE", "campaign.snapshot.read");
        insertClient(CAMPAIGN_ID, "campaign-secret", "ACTIVE", "catalog.read");
    }

    @Test
    void flashSaleClientGetsOnlyItsSnapshotScopeAndOwnSubject() throws Exception {
        MvcResult result = requestToken(FLASHSALE_ID, FLASHSALE_SECRET, "campaign.snapshot.read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Jwt token = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                .validateType(false)
                .build()
                .decode(body.path("access_token").asText());

        assertThat(token.getSubject()).isEqualTo(FLASHSALE_ID);
        assertThat(token.getAudience()).containsExactly("flash-sale-internal-api");
        assertThat(String.valueOf(token.getClaims().get("scope")))
                .contains("campaign.snapshot.read")
                .doesNotContain("catalog.read");
    }

    @Test
    void flashSaleClientCannotRequestCampaignOrInventoryScopes() throws Exception {
        requestToken(FLASHSALE_ID, FLASHSALE_SECRET, "catalog.read")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").isString());

        requestToken(CAMPAIGN_ID, "campaign-secret", "campaign.snapshot.read")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").isString());
    }

    private ResultActions requestToken(
            String clientId, String secret, String scope) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                .with(httpBasic(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("scope", scope));
    }

    private void insertClient(String clientId, String secret, String status, String scope) {
        UUID id = UUID.randomUUID();
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(secret);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO oauth_clients (
                    id, client_id, client_secret_hash, grant_type, status,
                    access_token_ttl_seconds, created_at, updated_at
                ) VALUES (?, ?, ?, 'client_credentials', ?, 300, ?, ?)
                """, id, clientId, hash, status, now, now);
        jdbc.update("INSERT INTO oauth_client_scopes (oauth_client_id, scope) VALUES (?, ?)", id, scope);
    }

    private static KeyPair createKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}
