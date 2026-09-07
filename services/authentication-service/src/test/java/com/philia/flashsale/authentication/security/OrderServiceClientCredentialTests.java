package com.philia.flashsale.authentication.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.authentication.configuration.ServiceClientsProperties;
import com.philia.flashsale.authentication.serviceclient.adapter.in.oauth.ServiceClientBootstrapper;
import com.philia.flashsale.authentication.serviceclient.application.ProvisionServiceClientService;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.DefaultApplicationArguments;
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

/** Locks Order's least-privilege client-credentials provisioning boundary. */
@SpringBootTest(properties = {
        "flashsale.authentication.http.enabled=true",
        "flashsale.authentication.core-enabled=true",
        "flashsale.authentication.runtime-enabled=true",
        "spring.liquibase.enabled=true"
})
@AutoConfigureMockMvc
@Testcontainers
class OrderServiceClientCredentialTests {

    private static final String ORDER_ID = "order-service";
    private static final String ORDER_SECRET = "order-secret";
    private static final String ISSUER = "http://authentication-service:8080";
    private static final KeyPair KEY_PAIR = createKeyPair();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("order_client_contract_db")
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
        registry.add("flashsale.auth.jwt.key-id", () -> "feature-049-order-key");
        registry.add("flashsale.auth.jwt.public-key-pem", () -> pem("PUBLIC KEY", KEY_PAIR.getPublic().getEncoded()));
        registry.add("flashsale.auth.jwt.private-key-pem", () -> pem("PRIVATE KEY", KEY_PAIR.getPrivate().getEncoded()));
    }

    @BeforeEach
    void resetClients() {
        jdbc.update("TRUNCATE TABLE oauth_client_scopes, oauth_clients");
        insertClient(ORDER_ID, ORDER_SECRET, List.of(
                "cart.checkout-snapshot.read",
                "catalog.purchase-quote.read",
                "inventory.regular-hold.write"));
    }

    @Test
    void provisionsOnlyTheThreeApprovedOrderCapabilitiesWithoutChangingExistingClients() throws Exception {
        ProvisionServiceClientService provisioner = mock(ProvisionServiceClientService.class);
        ServiceClientsProperties properties = new ServiceClientsProperties(
                new ServiceClientsProperties.Client("campaign-service", "campaign-secret", List.of("catalog.read")),
                new ServiceClientsProperties.Client("flashsale-service", "flashsale-secret", List.of("campaign.snapshot.read")),
                new ServiceClientsProperties.Client("cart-service", "cart-secret", List.of("catalog.variant-display.read")),
                new ServiceClientsProperties.Client("order-service", "order-secret", List.of(
                        "cart.checkout-snapshot.read",
                        "catalog.purchase-quote.read",
                        "inventory.regular-hold.write")));

        new ServiceClientBootstrapper(provisioner, properties).run(new DefaultApplicationArguments());

        verify(provisioner).provisionIfConfigured("order-service", "order-secret", List.of(
                "cart.checkout-snapshot.read", "catalog.purchase-quote.read", "inventory.regular-hold.write"));
        verify(provisioner).provisionIfConfigured("campaign-service", "campaign-secret", List.of("catalog.read"));
        verify(provisioner).provisionIfConfigured("flashsale-service", "flashsale-secret", List.of("campaign.snapshot.read"));
        verify(provisioner).provisionIfConfigured("cart-service", "cart-secret", List.of("catalog.variant-display.read"));
        verifyNoMoreInteractions(provisioner);
    }

    @Test
    void orderTokenHasOnlyApprovedScopesAndInternalMachineIdentity() throws Exception {
        MvcResult result = requestToken("cart.checkout-snapshot.read catalog.purchase-quote.read")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        Jwt token = NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEY_PAIR.getPublic())
                .validateType(false)
                .build()
                .decode(body.path("access_token").asText());

        assertThat(token.getSubject()).isEqualTo(ORDER_ID);
        assertThat(token.getAudience()).containsExactly("flash-sale-internal-api");
        assertThat(String.valueOf(token.getClaims().get("scope")))
                .contains("cart.checkout-snapshot.read", "catalog.purchase-quote.read")
                .doesNotContain("inventory.campaign.allocate");
    }

    @Test
    void orderClientCannotRequestAnotherServiceCapability() throws Exception {
        requestToken("inventory.campaign.allocate")
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.error").isString());
    }

    private ResultActions requestToken(String scope) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                .with(httpBasic(ORDER_ID, ORDER_SECRET))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("grant_type", "client_credentials")
                .param("scope", scope));
    }

    private void insertClient(String clientId, String secret, List<String> scopes) {
        UUID id = UUID.randomUUID();
        String hash = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8().encode(secret);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO oauth_clients (
                    id, client_id, client_secret_hash, grant_type, status,
                    access_token_ttl_seconds, created_at, updated_at
                ) VALUES (?, ?, ?, 'client_credentials', 'ACTIVE', 300, ?, ?)
                """, id, clientId, hash, now, now);
        for (String scope : scopes) {
            jdbc.update("INSERT INTO oauth_client_scopes (oauth_client_id, scope) VALUES (?, ?)", id, scope);
        }
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
