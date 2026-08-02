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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

/**
 * Contract tests for the approved OAuth2 Client Credentials endpoint.
 *
 * <p>The endpoint is intentionally tested before its Feature 017 implementation is wired. The
 * tests provide the executable contract for T034 and may remain red until later implementation
 * tasks are complete.</p>
 */
@SpringBootTest(properties = {
        "flashsale.authentication.http.enabled=true",
        "flashsale.authentication.core-enabled=true",
        "flashsale.authentication.runtime-enabled=true",
        "spring.liquibase.enabled=true"
})
@AutoConfigureMockMvc
@Testcontainers
class ClientCredentialsTokenEndpointTests {

    private static final String CLIENT_ID = "campaign-service";
    private static final String CLIENT_SECRET = "campaign-secret";
    private static final String INTERNAL_AUDIENCE = "flash-sale-internal-api";
    private static final String ISSUER = "http://authentication-service:8080";
    private static final KeyPair KEY_PAIR = createKeyPair();
    private static final String PUBLIC_KEY_PEM = publicKeyPem((RSAPublicKey) KEY_PAIR.getPublic());
    private static final String PRIVATE_KEY_PEM = privateKeyPem((RSAPrivateKey) KEY_PAIR.getPrivate());

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("auth_token_contract_db")
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
        registry.add("flashsale.auth.jwt.key-id", () -> "t034-test-key");
        registry.add("flashsale.auth.jwt.public-key-pem", () -> PUBLIC_KEY_PEM);
        registry.add("flashsale.auth.jwt.private-key-pem", () -> PRIVATE_KEY_PEM);
    }

    @BeforeEach
    void resetClientRegistry() {
        jdbc.update("TRUNCATE TABLE oauth_client_scopes, oauth_clients");
        insertClient(CLIENT_ID, CLIENT_SECRET, "ACTIVE", List.of(
                "catalog.read", "inventory.campaign.allocate"));
        insertClient("inactive-campaign-service", "inactive-secret", "INACTIVE", List.of("catalog.read"));
    }

    @Test
    void validCampaignCredentialsIssueInternalServiceTokenWithoutRefreshToken() throws Exception {
        MvcResult result = tokenRequest(CLIENT_ID, CLIENT_SECRET, "client_credentials",
                "catalog.read inventory.campaign.allocate")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").isNumber())
                .andExpect(jsonPath("$.scope").value("catalog.read inventory.campaign.allocate"))
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.path("access_token").asText();
        assertThat(accessToken).isNotBlank();

        Jwt jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                .build()
                .decode(accessToken);
        assertThat(jwt.getIssuer()).hasToString(ISSUER);
        assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getAudience()).containsExactly(INTERNAL_AUDIENCE);
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getExpiresAt()).isNotNull();
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds())
                .isPositive()
                .isLessThanOrEqualTo(300);
        assertThat(scopeValues(jwt)).containsExactlyInAnyOrder(
                "catalog.read", "inventory.campaign.allocate");
    }

    @ParameterizedTest(name = "rejects {0}")
    @MethodSource("rejectedRequests")
    void rejectedClientCredentialsRequestsReturnStandardOauthError(
            String scenario, String clientId, String secret, String grantType, String scope) throws Exception {
        tokenRequest(clientId, secret, grantType, scope)
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.access_token").doesNotExist())
                .andExpect(jsonPath("$.refresh_token").doesNotExist());
    }

    private static Stream<Arguments> rejectedRequests() {
        return Stream.of(
                Arguments.of("unknown client", "unknown-campaign-service", CLIENT_SECRET,
                        "client_credentials", "catalog.read"),
                Arguments.of("wrong secret", CLIENT_ID, "wrong-secret",
                        "client_credentials", "catalog.read"),
                Arguments.of("inactive client", "inactive-campaign-service", "inactive-secret",
                        "client_credentials", "catalog.read"),
                Arguments.of("disallowed grant", CLIENT_ID, CLIENT_SECRET,
                        "authorization_code", "catalog.read"),
                Arguments.of("disallowed scope", CLIENT_ID, CLIENT_SECRET,
                        "client_credentials", "catalog.write"));
    }

    private ResultActions tokenRequest(
            String clientId, String secret, String grantType, String scope) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                .with(httpBasic(clientId, secret))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", grantType)
                .param("scope", scope));
    }

    private void insertClient(String clientId, String secret, String status, List<String> scopes) {
        UUID id = UUID.randomUUID();
        String secretHash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(secret);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO oauth_clients (
                    id, client_id, client_secret_hash, grant_type, status,
                    access_token_ttl_seconds, created_at, updated_at
                ) VALUES (?, ?, ?, 'client_credentials', ?, 300, ?, ?)
                """, id, clientId, secretHash, status, now, now);
        for (String scope : scopes) {
            jdbc.update("INSERT INTO oauth_client_scopes (oauth_client_id, scope) VALUES (?, ?)", id, scope);
        }
    }

    private static List<String> scopeValues(Jwt jwt) {
        Object claim = jwt.getClaims().get("scope");
        if (claim instanceof String value) {
            return List.of(value.split(" "));
        }
        if (claim instanceof List<?> values) {
            return values.stream().map(String::valueOf).toList();
        }
        return List.of();
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

    private static String publicKeyPem(RSAPublicKey key) {
        return pem("PUBLIC KEY", key.getEncoded());
    }

    private static String privateKeyPem(RSAPrivateKey key) {
        return pem("PRIVATE KEY", key.getEncoded());
    }

    private static String pem(String type, byte[] encoded) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded)
                + "\n-----END " + type + "-----";
    }
}
