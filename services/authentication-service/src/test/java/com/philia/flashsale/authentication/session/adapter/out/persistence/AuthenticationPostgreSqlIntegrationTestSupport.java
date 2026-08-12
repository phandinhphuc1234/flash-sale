package com.philia.flashsale.authentication.session.adapter.out.persistence;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import com.philia.flashsale.authentication.account.adapter.out.persistence.AccountJpaEntity;
import com.philia.flashsale.authentication.account.adapter.out.persistence.AccountJpaRepository;
import com.philia.flashsale.authentication.account.domain.AccountRole;
import com.philia.flashsale.authentication.account.domain.AccountStatus;
import com.philia.flashsale.authentication.session.domain.LoginSessionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "flashsale.authentication.http.enabled=false",
                "flashsale.authentication.core-enabled=false",
                "flashsale.authentication.runtime-enabled=true",
                "spring.main.web-application-type=none",
                "management.health.redis.enabled=false"
        })
abstract class AuthenticationPostgreSqlIntegrationTestSupport {

    private static final String PUBLIC_KEY_PEM = createPublicKeyPem();

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("auth_db")
                    .withUsername("auth")
                    .withPassword("auth");

    static {
        // Keep one real database alive for every persistence test class sharing Spring's context.
        // Testcontainers' Ryuk process still removes it when the Maven test JVM exits.
        POSTGRES.start();
    }

    @Autowired
    protected AccountJpaRepository accounts;

    @Autowired
    protected LoginSessionJpaRepository sessions;

    @Autowired
    protected RefreshCredentialJpaRepository refreshCredentials;

    @Autowired
    protected JpaAuthenticationSessionPersistenceAdapter persistenceAdapter;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("flashsale.auth.jwt.public-key-pem", () -> PUBLIC_KEY_PEM);
    }

    @BeforeEach
    void clearAuthenticationData() {
        jdbc.execute("TRUNCATE TABLE refresh_tokens, user_sessions, users");
    }

    protected SessionFixture createCurrentSession(String tokenHash, Instant now) {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID refreshId = UUID.randomUUID();
        accounts.saveAndFlush(new AccountJpaEntity(
                userId,
                userId + "@example.test",
                userId + "@example.test",
                null,
                null,
                "$argon2id$test-proof",
                AccountRole.ROLE_USER,
                AccountStatus.ACTIVE,
                null,
                null,
                now.minusSeconds(3600),
                now.minusSeconds(3600)));
        sessions.saveAndFlush(new LoginSessionJpaEntity(
                sessionId,
                userId,
                LoginSessionStatus.ACTIVE,
                "integration-test",
                "JUnit",
                "127.0.0.1",
                now.minusSeconds(3600),
                now.minusSeconds(60),
                now.plusSeconds(86_400),
                null,
                null));
        refreshCredentials.saveAndFlush(new RefreshCredentialJpaEntity(
                refreshId,
                sessionId,
                tokenHash,
                null,
                null,
                now.minusSeconds(600),
                now.plusSeconds(3600),
                null,
                null,
                null));
        return new SessionFixture(userId, sessionId, refreshId, tokenHash);
    }

    protected static String hash(char value) {
        return String.valueOf(value).repeat(64);
    }

    private static String createPublicKeyPem() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            return "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(pair.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----";
        } catch (Exception exception) {
            throw new IllegalStateException("Could not create the test RSA public key", exception);
        }
    }

    protected record SessionFixture(UUID userId, UUID sessionId, UUID refreshId, String tokenHash) {
    }
}
