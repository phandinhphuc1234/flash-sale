package com.philia.flashsale.authentication.serviceclient.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the durable OAuth client registry contract against PostgreSQL.
 *
 * <p>The OAuth client migration is intentionally added by T009. These tests are
 * the red-first contract for client identity, scope, status, TTL, and secret
 * storage invariants.</p>
 */
@Testcontainers
class ServiceClientSchemaMigrationIntegrationTests {

    private static final String CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml";
    private static final OffsetDateTime NOW = OffsetDateTime.of(2030, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final String ARGON2_HASH =
            "$argon2id$v=19$m=19456,t=2,p=1$c2FsdC10ZXN0LTEyMzQ1Ng$YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXo1234567890";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("auth_clients_db")
            .withUsername("auth")
            .withPassword("auth");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateDatabase() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        newLiquibase(dataSource).afterPropertiesSet();
    }

    @Test
    void migrationCreatesClientAndScopeTables() {
        assertThat(publicTables()).containsExactlyInAnyOrder(
                "users",
                "user_sessions",
                "refresh_tokens",
                "oauth_clients",
                "oauth_client_scopes",
                "databasechangelog",
                "databasechangeloglock");

        assertThat(jdbc.queryForList("SELECT id FROM databasechangelog", String.class))
                .contains("authentication-003-create-oauth-client-schema");
        assertThat(jdbc.queryForObject("SELECT locked FROM databasechangeloglock WHERE id = 1", Boolean.class))
                .isFalse();
    }

    @Test
    void clientIdentityAndSecretAreRequiredAndClientIdIsUnique() {
        String clientId = "client-" + UUID.randomUUID();
        insertClient(clientId, ARGON2_HASH, "ACTIVE", "client_credentials", 300);

        assertThatThrownBy(() -> insertClient(clientId, ARGON2_HASH, "ACTIVE", "client_credentials", 300))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertClient(
                "client-" + UUID.randomUUID(), null, "ACTIVE", "client_credentials", 300))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void clientStatusGrantAndTtlAreConstrained() {
        insertClient("inactive-" + UUID.randomUUID(), ARGON2_HASH, "INACTIVE", "client_credentials", 300);

        assertThatThrownBy(() -> insertClient(
                "invalid-status-" + UUID.randomUUID(), ARGON2_HASH, "DISABLED", "client_credentials", 300))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertClient(
                "invalid-grant-" + UUID.randomUUID(), ARGON2_HASH, "ACTIVE", "authorization_code", 300))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertClient(
                "zero-ttl-" + UUID.randomUUID(), ARGON2_HASH, "ACTIVE", "client_credentials", 0))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertClient(
                "long-ttl-" + UUID.randomUUID(), ARGON2_HASH, "ACTIVE", "client_credentials", 301))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void scopesAreUniqueAndMustReferenceAnExistingClient() {
        String clientId = "scoped-" + UUID.randomUUID();
        UUID clientDbId = insertClient(clientId, ARGON2_HASH, "ACTIVE", "client_credentials", 300);

        insertScope(clientDbId, "catalog.read");
        assertThatThrownBy(() -> insertScope(clientDbId, "catalog.read"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertScope(UUID.randomUUID(), "inventory.campaign.allocate"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> insertScope(clientDbId, null))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void clientSecretColumnAcceptsEncodedArgon2ValueButRejectsPlaintext() {
        String clientId = "hashed-" + UUID.randomUUID();
        UUID clientDbId = insertClient(clientId, ARGON2_HASH, "ACTIVE", "client_credentials", 300);

        assertThat(jdbc.queryForObject(
                "SELECT client_secret_hash FROM oauth_clients WHERE id = ?", String.class, clientDbId))
                .startsWith("$argon2id$")
                .doesNotContain("plain-secret");

        assertThatThrownBy(() -> insertClient(
                "plain-" + UUID.randomUUID(), "plain-secret", "ACTIVE", "client_credentials", 300))
                .isInstanceOf(DataAccessException.class);
    }

    private static SpringLiquibase newLiquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setShouldRun(true);
        return liquibase;
    }

    private static UUID insertClient(
            String clientId,
            String secretHash,
            String status,
            String grantType,
            int ttlSeconds) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO oauth_clients (
                    id, client_id, client_secret_hash, grant_type, status,
                    access_token_ttl_seconds, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, clientId, secretHash, grantType, status, ttlSeconds, NOW, NOW);
        return id;
    }

    private static void insertScope(UUID clientId, String scope) {
        jdbc.update("""
                INSERT INTO oauth_client_scopes (oauth_client_id, scope)
                VALUES (?, ?)
                """, clientId, scope);
    }

    private static List<String> publicTables() {
        return jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """, String.class);
    }
}
