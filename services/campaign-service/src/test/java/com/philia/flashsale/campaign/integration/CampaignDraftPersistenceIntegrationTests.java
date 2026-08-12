package com.philia.flashsale.campaign.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.CampaignPersistenceAdapter;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.entity.CampaignJpaEntity;
import com.philia.flashsale.campaign.campaign.adapter.out.persistence.jpa.repository.CampaignJpaRepository;
import com.philia.flashsale.campaign.campaign.domain.model.Campaign;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignItem;
import com.philia.flashsale.campaign.campaign.domain.model.CampaignMoney;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** PostgreSQL integration coverage for the editable Campaign aggregate boundary. */
@SpringBootTest
@Testcontainers
class CampaignDraftPersistenceIntegrationTests {

    @MockBean
    private ClientRegistrationRepository clientRegistrations;

    private static final Instant CREATED_AT = Instant.parse("2026-07-30T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-30T11:00:00Z");
    private static final Instant START = Instant.parse("2026-08-01T10:00:00Z");
    private static final Instant END = Instant.parse("2026-08-01T11:00:00Z");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("campaign_db")
            .withUsername("campaign")
            .withPassword("campaign");

    @Autowired
    private CampaignPersistenceAdapter persistenceAdapter;

    @Autowired
    private CampaignJpaRepository campaignRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @PersistenceContext
    private EntityManager entityManager;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void clearBusinessData() {
        jdbc.execute("TRUNCATE TABLE campaign_outbox_events, campaign_schedule_operations, "
                + "campaign_items, campaigns");
    }

    @Test
    void enforcesCaseInsensitiveCodeUniquenessAfterDomainNormalization() {
        persist(Campaign.createDraft(
                UUID.randomUUID(), " summer-unique ", "First", START, END, "creator", CREATED_AT));
        Campaign duplicateWithDifferentCase = Campaign.createDraft(
                UUID.randomUUID(), "SUMMER-UNIQUE", "Duplicate", START, END, "creator", CREATED_AT);

        assertThatThrownBy(() -> inTransaction(() -> persistenceAdapter.save(duplicateWithDifferentCase)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM campaigns WHERE UPPER(code) = UPPER(?)",
                Integer.class,
                "summer-unique")).isEqualTo(1);
    }

    @Test
    void rejectsStaleOptimisticVersionWithoutOverwritingNewerCampaign() {
        Campaign saved = persist(Campaign.createDraft(
                UUID.randomUUID(), "VERSION-001", "Original", START, END, "creator", CREATED_AT));
        CampaignJpaEntity first = loadEntity(saved.id());
        CampaignJpaEntity second = loadEntity(saved.id());

        first.setName("First writer");
        inTransaction(() -> campaignRepository.save(first));

        second.setName("Stale writer");
        assertThatThrownBy(() -> inTransaction(() -> {
            campaignRepository.save(second);
            entityManager.flush();
        })).isInstanceOf(ObjectOptimisticLockingFailureException.class);

        assertThat(jdbc.queryForObject(
                "SELECT name FROM campaigns WHERE id = ?", String.class, saved.id()))
                .isEqualTo("First writer");
        assertThat(jdbc.queryForObject(
                "SELECT version FROM campaigns WHERE id = ?", Long.class, saved.id()))
                .isEqualTo(1L);
    }

    @Test
    void rollsBackItemReplacementAtomically() {
        Campaign original = Campaign.createDraft(
                UUID.randomUUID(), "ITEM-ATOMIC-001", "Item campaign", START, END, "creator", CREATED_AT);
        Campaign saved = persist(original);

        UUID originalVariant = UUID.randomUUID();
        Campaign withItem = persistenceAdapter.findById(saved.id()).orElseThrow();
        withItem.replaceItem(draftItem(originalVariant, 100), "creator", CREATED_AT.plusSeconds(1));
        Campaign savedWithItem = persist(withItem);

        Campaign replacement = persistenceAdapter.findById(savedWithItem.id()).orElseThrow();
        UUID replacementVariant = UUID.randomUUID();
        replacement.replaceItem(draftItem(replacementVariant, 200), "editor", UPDATED_AT);

        inTransactionRollback(() -> persistenceAdapter.save(replacement));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT variant_id, campaign_price, requested_quantity FROM campaign_items WHERE campaign_id = ?",
                saved.id());
        assertThat(row.get("variant_id")).isEqualTo(originalVariant);
        assertThat(row.get("campaign_price")).isEqualTo(new BigDecimal("100.0000"));
        assertThat(row.get("requested_quantity")).isEqualTo(100L);
        assertThat(jdbc.queryForObject(
                "SELECT version FROM campaigns WHERE id = ?", Long.class, saved.id()))
                .isEqualTo(1L);
    }

    @Test
    void persistsAuditActorsAndTimesAlongsideCampaignVersion() {
        Campaign original = Campaign.createDraft(
                UUID.randomUUID(), "AUDIT-001", "Original", START, END, "creator", CREATED_AT);
        Campaign saved = persist(original);

        Campaign updated = persistenceAdapter.findById(saved.id()).orElseThrow();
        updated.replaceMetadata("Updated", START.plusSeconds(300), END.plusSeconds(300), "editor", UPDATED_AT);
        persist(updated);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT created_by, updated_by, created_at, updated_at, version FROM campaigns WHERE id = ?",
                saved.id());
        assertThat(row.get("created_by")).isEqualTo("creator");
        assertThat(row.get("updated_by")).isEqualTo("editor");
        assertThat(toInstant(row.get("created_at"))).isEqualTo(CREATED_AT);
        assertThat(toInstant(row.get("updated_at"))).isAfter(CREATED_AT);
        assertThat(row.get("version")).isEqualTo(1L);
    }

    @Test
    void rootRowLockIsAcquiredBeforeTheAggregateGraphIsRead() throws Exception {
        Campaign saved = persist(Campaign.createDraft(
                UUID.randomUUID(), "LOCK-ORDER-001", "Original", START, END, "creator", CREATED_AT));
        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch allowFirstCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> first = executor.submit(() -> {
                inTransaction(() -> {
                    campaignRepository.lockRootById(saved.id()).orElseThrow();
                    CampaignJpaEntity entity = campaignRepository.findDetailedById(saved.id()).orElseThrow();
                    entity.setName("First writer");
                    firstHasLock.countDown();
                    await(allowFirstCommit);
                    campaignRepository.saveAndFlush(entity);
                });
                return null;
            });
            assertThat(firstHasLock.await(10, TimeUnit.SECONDS)).isTrue();
            Future<String> second = executor.submit(() -> inTransaction(() -> {
                campaignRepository.lockRootById(saved.id()).orElseThrow();
                return campaignRepository.findDetailedById(saved.id()).orElseThrow().getName();
            }));

            allowFirstCommit.countDown();

            first.get(10, TimeUnit.SECONDS);
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo("First writer");
        } finally {
            allowFirstCommit.countDown();
            executor.shutdownNow();
        }
    }

    private Campaign persist(Campaign campaign) {
        return inTransaction(() -> persistenceAdapter.save(campaign));
    }

    private CampaignJpaEntity loadEntity(UUID campaignId) {
        return inTransaction(() -> campaignRepository.findDetailedById(campaignId).orElseThrow());
    }

    private <T> T inTransaction(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

    private void inTransaction(Runnable operation) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> operation.run());
    }

    private void inTransactionRollback(Runnable operation) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            operation.run();
            status.setRollbackOnly();
        });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Campaign lock test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Campaign lock test was interrupted", exception);
        }
    }

    private static CampaignItem draftItem(UUID variantId, long amount) {
        return CampaignItem.createDraft(
                null,
                variantId,
                CampaignMoney.vnd(BigDecimal.valueOf(amount)),
                amount,
                1);
    }

    private static Instant toInstant(Object value) {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new AssertionError("Unexpected PostgreSQL timestamp type: " + value);
    }
}
