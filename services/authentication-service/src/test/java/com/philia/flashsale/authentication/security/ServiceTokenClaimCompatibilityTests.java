package com.philia.flashsale.authentication.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountRole;
import com.philia.flashsale.authentication.account.domain.AccountStatus;
import com.philia.flashsale.authentication.configuration.JwtTrustProperties;
import com.philia.flashsale.authentication.security.token.JwtAccessTokenAdapter;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Locks the trust boundary between public administrator JWTs and internal service JWTs.
 *
 * <p>The administrator assertion is green today. The service-token assertion is intentionally
 * executable before the OAuth2 client-credentials implementation (T047), so its expected red
 * result prevents the future implementation from silently reusing the public audience.</p>
 */
@SpringBootTest(properties = {
        "flashsale.authentication.http.enabled=true",
        "flashsale.authentication.core-enabled=true",
        "flashsale.authentication.runtime-enabled=true",
        "spring.liquibase.enabled=true"
})
@AutoConfigureMockMvc
@Testcontainers
class ServiceTokenClaimCompatibilityTests {

    private static final String CLIENT_ID = "campaign-service";
    private static final String CLIENT_SECRET = "campaign-secret";
    private static final String ISSUER = "http://authentication-service:8080";
    private static final String PUBLIC_AUDIENCE = "flash-sale-api";
    private static final String INTERNAL_AUDIENCE = "flash-sale-internal-api";
    private static final KeyPair KEY_PAIR = createKeyPair();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("auth_service_token_claims_db")
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
        registry.add("flashsale.auth.jwt.audience", () -> PUBLIC_AUDIENCE);
        registry.add("flashsale.auth.jwt.key-id", () -> "t036-test-key");
        registry.add("flashsale.auth.jwt.public-key-pem",
                () -> pem("PUBLIC KEY", ((RSAPublicKey) KEY_PAIR.getPublic()).getEncoded()));
        registry.add("flashsale.auth.jwt.private-key-pem",
                () -> pem("PRIVATE KEY", ((RSAPrivateKey) KEY_PAIR.getPrivate()).getEncoded()));
    }

    @BeforeEach
    void seedCampaignClient() {
        jdbc.update("TRUNCATE TABLE oauth_client_scopes, oauth_clients");
        String secretHash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(CLIENT_SECRET);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UUID clientId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oauth_clients (
                    id, client_id, client_secret_hash, grant_type, status,
                    access_token_ttl_seconds, created_at, updated_at
                ) VALUES (?, ?, ?, 'client_credentials', 'ACTIVE', 300, ?, ?)
                """, clientId, CLIENT_ID, secretHash, now, now);
        jdbc.update("INSERT INTO oauth_client_scopes (oauth_client_id, scope) VALUES (?, ?)",
                clientId, "catalog.read");
    }

    @Test
    void administratorTokenRetainsPublicAudienceAndAuthorityBoundary() throws Exception {
        JwtAccessTokenAdapter adapter = new JwtAccessTokenAdapter(
                new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(
                        new RSAKey.Builder((RSAPublicKey) KEY_PAIR.getPublic())
                                .privateKey((RSAPrivateKey) KEY_PAIR.getPrivate())
                                .keyID("t036-test-key")
                                .build()))),
                new JwtTrustProperties(ISSUER, PUBLIC_AUDIENCE, "t036-test-key", null, null, null, null));
        Instant issuedAt = Instant.now();
        Account admin = Account.restore(UUID.randomUUID(), "admin@example.test", "admin@example.test", "admin",
                "admin", "argon-hash", AccountRole.ROLE_ADMIN, AccountStatus.ACTIVE, null, null,
                issuedAt, issuedAt);

        Jwt jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                .validateType(false)
                .build()
                .decode(adapter.issueAccessToken(admin, UUID.randomUUID(), issuedAt));

        assertThat(jwt.getAudience()).containsExactly(PUBLIC_AUDIENCE);
        assertThat(jwt.getAudience()).doesNotContain(INTERNAL_AUDIENCE);
        assertThat(jwt.getClaimAsStringList("authorities"))
                .contains("ROLE_ADMIN", "CATALOG_ADMIN", "INVENTORY_ADMIN", "CAMPAIGN_ADMIN");
    }

    @Test
    void serviceTokenIsRs256InternalAudienceScopeBoundAndAtMostFiveMinutes() throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType("application/x-www-form-urlencoded")
                        .param("grant_type", "client_credentials")
                        .param("scope", "catalog.read"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Jwt jwt = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                .validateType(false)
                .build()
                .decode(body.path("access_token").asText());

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256").containsEntry("typ", "at+jwt");
        assertThat(jwt.getIssuer()).hasToString(ISSUER);
        assertThat(jwt.getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getAudience()).containsExactly(INTERNAL_AUDIENCE);
        assertThat(scopeValues(jwt)).containsExactly("catalog.read");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).getSeconds())
                .isPositive().isLessThanOrEqualTo(300);
    }

    private static List<String> scopeValues(Jwt jwt) {
        Object scope = jwt.getClaims().get("scope");
        return scope instanceof String value ? List.of(value.split(" ")) : List.of();
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
