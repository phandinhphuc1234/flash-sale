package com.philia.flashsale.authentication;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.sql.DataSource;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Verifies the service-owned Auth schema can be applied repeatedly and has safe rollback declarations. */
@Testcontainers
class AuthenticationMigrationIT {

    private static final String CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml";
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "users", "user_sessions", "refresh_tokens", "oauth_clients", "oauth_client_scopes",
            "databasechangelog", "databasechangeloglock");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("auth_db")
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
    void migrationCreatesAuthTablesAndLiquibaseLedger() {
        assertThat(publicTables()).containsExactlyInAnyOrderElementsOf(EXPECTED_TABLES);

        List<Map<String, Object>> changes = jdbc.queryForList("""
                SELECT id, author, exectype
                FROM databasechangelog
                ORDER BY orderexecuted
                """);
        assertThat(changes).hasSize(3);
        assertThat(changes.getFirst())
                .containsEntry("id", "authentication-001-create-schema")
                .containsEntry("author", "flashsale")
                .containsEntry("exectype", "EXECUTED");
        assertThat(changes.get(1))
                .containsEntry("id", "authentication-002-align-refresh-token-hash-type")
                .containsEntry("author", "flashsale")
                .containsEntry("exectype", "EXECUTED");
        assertThat(changes.get(2))
                .containsEntry("id", "authentication-003-create-oauth-client-schema")
                .containsEntry("author", "flashsale")
                .containsEntry("exectype", "EXECUTED");
        assertThat(jdbc.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'refresh_tokens'
                  AND column_name = 'token_hash'
                """, String.class)).isEqualTo("character varying");
        assertThat(jdbc.queryForObject("SELECT locked FROM databasechangeloglock WHERE id = 1", Boolean.class))
                .isFalse();
    }

    @Test
    void secondLiquibaseInvocationIsIdempotent() throws Exception {
        List<String> before = publicTables();
        newLiquibase(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())).afterPropertiesSet();

        assertThat(publicTables()).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM databasechangelog", Integer.class)).isEqualTo(3);
    }

    @Test
    void changesetDocumentsReverseDependencyRollbackWithoutCascade() throws IOException {
        String migrationSql;
        try (var input = new ClassPathResource(
                "db/changelog/changes/001-create-authentication-schema.sql").getInputStream()) {
            migrationSql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        String rollbackSection = migrationSql.substring(migrationSql.indexOf("--rollback"));
        assertThat(rollbackSection)
                .containsSubsequence(
                        "--rollback DROP TABLE refresh_tokens;",
                        "--rollback DROP TABLE user_sessions;",
                        "--rollback DROP TABLE users;")
                .doesNotContainIgnoringCase("CASCADE");
    }

    private static SpringLiquibase newLiquibase(DataSource dataSource) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(CHANGELOG);
        liquibase.setShouldRun(true);
        return liquibase;
    }

    private List<String> publicTables() {
        return jdbc.queryForList("""
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """, String.class);
    }
}
