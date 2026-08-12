package com.philia.flashsale.authentication.serviceclient.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PostgreSQL persistence contracts for the durable OAuth service-client registry.
 *
 * <p>The small JDBC fixture models the persistence boundary that T045-T047 will replace with the
 * feature-owned adapter. Keeping these checks against real PostgreSQL catches schema/type and
 * uniqueness regressions before the Spring Authorization Server adapter is wired.</p>
 */
@Testcontainers
class ServiceClientPersistenceIntegrationTests {

    private static final String CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml";
    private static final OffsetDateTime NOW = OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final Argon2PasswordEncoder PASSWORD_ENCODER =
            Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("auth_clients_persistence_db")
            .withUsername("auth")
            .withPassword("auth");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabase() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setShouldRun(true);
        liquibase.afterPropertiesSet();
    }

    @Test
    void persistsArgon2SecretAndNeverStoresRawCredential() {
        String clientId = "hashed-" + UUID.randomUUID();
        String rawSecret = "campaign-secret";
        String encodedSecret = PASSWORD_ENCODER.encode(rawSecret);
        UUID clientDbId = insertClient(clientId, encodedSecret, "ACTIVE");

        String stored = jdbc.queryForObject(
                "SELECT client_secret_hash FROM oauth_clients WHERE id = ?", String.class, clientDbId);

        assertThat(stored).startsWith("$argon2id$")
                .doesNotContain(rawSecret);
        assertThat(PASSWORD_ENCODER.matches(rawSecret, stored)).isTrue();
    }

    @Test
    void loadsOnlyScopesOwnedByRequestedClient() {
        UUID first = insertClient("scopes-a-" + UUID.randomUUID(), PASSWORD_ENCODER.encode("secret-a"), "ACTIVE");
        UUID second = insertClient("scopes-b-" + UUID.randomUUID(), PASSWORD_ENCODER.encode("secret-b"), "ACTIVE");
        insertScopes(first, "catalog.read", "inventory.campaign.allocate");
        insertScopes(second, "campaign.snapshot.read");

        List<String> scopes = jdbc.queryForList("""
                SELECT scope
                FROM oauth_client_scopes
                WHERE oauth_client_id = ?
                ORDER BY scope
                """, String.class, first);

        assertThat(scopes).containsExactly("catalog.read", "inventory.campaign.allocate");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth_client_scopes WHERE oauth_client_id = ?", Integer.class, second))
                .isEqualTo(1);
    }

    @Test
    void preservesInactiveStatusForAuthorizationLayerToReject() {
        String clientId = "inactive-" + UUID.randomUUID();
        UUID clientDbId = insertClient(clientId, PASSWORD_ENCODER.encode("inactive-secret"), "INACTIVE");

        assertThat(jdbc.queryForObject(
                "SELECT status FROM oauth_clients WHERE id = ?", String.class, clientDbId))
                .isEqualTo("INACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth_clients WHERE client_id = ? AND status = 'ACTIVE'",
                Integer.class, clientId))
                .isZero();
    }

    @Test
    void fixedClientProvisioningIsIdempotentAndDoesNotOverwriteExistingSecret() {
        String clientId = "fixed-campaign-service-" + UUID.randomUUID();
        String firstSecretHash = PASSWORD_ENCODER.encode("first-secret");
        String secondSecretHash = PASSWORD_ENCODER.encode("second-secret");

        provisionIfMissing(clientId, firstSecretHash, List.of("catalog.read"));
        provisionIfMissing(clientId, secondSecretHash, List.of("catalog.read", "inventory.campaign.allocate"));

        UUID clientDbId = jdbc.queryForObject(
                "SELECT id FROM oauth_clients WHERE client_id = ?", UUID.class, clientId);
        String storedSecret = jdbc.queryForObject(
                "SELECT client_secret_hash FROM oauth_clients WHERE id = ?", String.class, clientDbId);
        List<String> scopes = jdbc.queryForList("""
                SELECT scope FROM oauth_client_scopes
                WHERE oauth_client_id = ? ORDER BY scope
                """, String.class, clientDbId);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth_clients WHERE client_id = ?", Integer.class, clientId))
                .isOne();
        assertThat(storedSecret).isEqualTo(firstSecretHash);
        assertThat(scopes).containsExactly("catalog.read");
    }

    @Test
    void clientIdentityIsUniqueAcrossProvisioningAttempts() {
        String clientId = "unique-" + UUID.randomUUID();
        String secretHash = PASSWORD_ENCODER.encode("secret");
        provisionIfMissing(clientId, secretHash, List.of());
        provisionIfMissing(clientId, secretHash, List.of());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM oauth_clients WHERE client_id = ?", Integer.class, clientId))
                .isOne();
    }

    private static UUID insertClient(String clientId, String secretHash, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oauth_clients (
                    id, client_id, client_secret_hash, grant_type, status,
                    access_token_ttl_seconds, created_at, updated_at
                ) VALUES (?, ?, ?, 'client_credentials', ?, 300, ?, ?)
                """, id, clientId, secretHash, status, NOW, NOW);
        return id;
    }

    private static void insertScopes(UUID clientId, String... scopes) {
        for (String scope : scopes) {
            jdbc.update("INSERT INTO oauth_client_scopes (oauth_client_id, scope) VALUES (?, ?)", clientId, scope);
        }
    }

    private static void provisionIfMissing(String clientId, String secretHash, List<String> scopes) {
        UUID existing = jdbc.query(
                "SELECT id FROM oauth_clients WHERE client_id = ?", result ->
                        result.next() ? (UUID) result.getObject("id") : null, clientId);
        UUID id = existing == null ? insertClient(clientId, secretHash, "ACTIVE") : existing;
        if (existing == null) {
            insertScopes(id, scopes.toArray(String[]::new));
        }
    }
}
