package com.philia.flashsale.product.adapter.out.persistence.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.philia.flashsale.product.application.command.CreateProductDraftCommand;
import com.philia.flashsale.product.application.exception.AdminProductNotFoundException;
import com.philia.flashsale.product.application.exception.DuplicateProductCodeException;
import com.philia.flashsale.product.application.exception.DuplicateProductSlugException;
import com.philia.flashsale.product.application.exception.IdempotencyKeyReusedException;
import com.philia.flashsale.product.application.port.in.BrowseAdminCatalogUseCase;
import com.philia.flashsale.product.application.port.in.CreateProductDraftUseCase;
import com.philia.flashsale.product.application.port.in.ViewAdminProductUseCase;
import com.philia.flashsale.product.application.port.out.LoadAdminProductPort;
import com.philia.flashsale.product.application.port.out.SaveAdminProductPort;
import com.philia.flashsale.product.application.query.BrowseAdminCatalogQuery;
import com.philia.flashsale.product.application.query.ViewAdminProductQuery;
import com.philia.flashsale.product.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.application.result.AdminPageMetadata;
import com.philia.flashsale.product.application.result.AdminProductSummaryResult;
import com.philia.flashsale.product.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.domain.model.CatalogAdminActor;
import com.philia.flashsale.product.domain.model.ProductAggregate;
import com.philia.flashsale.product.domain.model.ProductStatus;
import com.philia.flashsale.product.domain.model.TraceId;

@SpringBootTest
@Testcontainers
@Import(ProductAdminDraftPersistenceIT.TestClockConfiguration.class)
class ProductAdminDraftPersistenceIT {

    private static final Instant INITIAL_TIME = Instant.parse("2026-07-20T00:00:00Z");
    private static final MutableClock TEST_CLOCK = new MutableClock(INITIAL_TIME, ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("product_db")
                    .withUsername("product")
                    .withPassword("product");

    @Autowired
    private CreateProductDraftUseCase createProductDraftUseCase;

    @Autowired
    private BrowseAdminCatalogUseCase browseAdminCatalogUseCase;

    @Autowired
    private ViewAdminProductUseCase viewAdminProductUseCase;

    @Autowired
    private SaveAdminProductPort saveAdminProductPort;

    @Autowired
    private LoadAdminProductPort loadAdminProductPort;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void clearBusinessData() {
        TEST_CLOCK.reset(INITIAL_TIME);
        jdbc.execute("""
                TRUNCATE TABLE
                    product_admin_audit_logs,
                    product_admin_idempotency_keys,
                    product_media,
                    product_categories,
                    product_variants,
                    products,
                    categories
                """);
    }

    @Test
    void createAndReplayPersistDraftIdempotencyAndDurableAudit() {
        CreateProductDraftCommand command = command(
                "PROD-PERSIST-001",
                "persist-product-001",
                "persist-key-1",
                "trace-1");

        CreateProductDraftResult saved = createProductDraftUseCase.createDraft(command);
        CreateProductDraftResult replayed = createProductDraftUseCase.createDraft(command);

        assertThat(saved.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(replayed.replayed()).isTrue();
        assertThat(replayed.id()).isEqualTo(saved.id());
        assertThat(loadAdminProductPort.loadAdminProduct(saved.id())).isPresent();
        assertThat(count("products")).isEqualTo(1);
        assertThat(count("product_admin_idempotency_keys")).isEqualTo(1);
        assertThat(auditOutcomes()).containsExactlyInAnyOrder("SUCCESS:", "REPLAYED:");
    }

    @Test
    @Timeout(20)
    void concurrentSameKeyRequestsCreateOneDraftAndReplayTheOther() throws Exception {
        CreateProductDraftCommand command = command(
                "PROD-CONCURRENT-001",
                "concurrent-product-001",
                "concurrent-key-1",
                "trace-concurrent");

        List<Object> outcomes = invokeConcurrently(
                () -> createProductDraftUseCase.createDraft(command),
                () -> createProductDraftUseCase.createDraft(command));

        assertThat(outcomes).allMatch(CreateProductDraftResult.class::isInstance);
        List<CreateProductDraftResult> results = outcomes.stream()
                .map(CreateProductDraftResult.class::cast)
                .toList();
        assertThat(results).extracting(CreateProductDraftResult::id).containsOnly(results.get(0).id());
        assertThat(results).extracting(CreateProductDraftResult::replayed)
                .containsExactlyInAnyOrder(false, true);
        assertThat(count("products")).isEqualTo(1);
        assertThat(count("product_admin_idempotency_keys")).isEqualTo(1);
        assertThat(auditOutcomes()).containsExactlyInAnyOrder("SUCCESS:", "REPLAYED:");
    }

    @Test
    void expiredKeyIsReclaimedForFreshCommandWhileAuditHistoryRemains() {
        CreateProductDraftResult first = createProductDraftUseCase.createDraft(command(
                "PROD-EXPIRY-001",
                "expiry-product-001",
                "expiry-key-1",
                "trace-expiry-1"));
        TEST_CLOCK.advance(Duration.ofDays(7));

        CreateProductDraftCommand freshCommand = command(
                "PROD-EXPIRY-002",
                "expiry-product-002",
                "expiry-key-1",
                "trace-expiry-2");
        CreateProductDraftResult fresh = createProductDraftUseCase.createDraft(freshCommand);
        CreateProductDraftResult replayedFresh = createProductDraftUseCase.createDraft(freshCommand);

        assertThat(fresh.replayed()).isFalse();
        assertThat(fresh.id()).isNotEqualTo(first.id());
        assertThat(replayedFresh.id()).isEqualTo(fresh.id());
        assertThat(replayedFresh.replayed()).isTrue();
        assertThat(count("products")).isEqualTo(2);
        assertThat(count("product_admin_idempotency_keys")).isEqualTo(1);
        assertThat(auditOutcomes()).containsExactlyInAnyOrder("SUCCESS:", "SUCCESS:", "REPLAYED:");
    }

    @Test
    void idempotencyConflictAuditSurvivesFailedMutationTransaction() {
        createProductDraftUseCase.createDraft(command(
                "PROD-IDEMPOTENT-001",
                "idempotent-product-001",
                "shared-key-1",
                "trace-idempotent-1"));

        assertThatThrownBy(() -> createProductDraftUseCase.createDraft(command(
                "PROD-IDEMPOTENT-002",
                "idempotent-product-002",
                "shared-key-1",
                "trace-idempotent-2")))
                .isInstanceOf(IdempotencyKeyReusedException.class);

        assertThat(count("products")).isEqualTo(1);
        assertThat(count("product_admin_idempotency_keys")).isEqualTo(1);
        assertThat(auditOutcomes()).containsExactlyInAnyOrder(
                "SUCCESS:",
                "CONFLICT:IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void duplicateAuditSurvivesFailedMutationTransaction() {
        createProductDraftUseCase.createDraft(command(
                "PROD-DUPLICATE-001",
                "duplicate-product-001",
                "duplicate-key-1",
                "trace-duplicate-1"));

        assertThatThrownBy(() -> createProductDraftUseCase.createDraft(command(
                "PROD-DUPLICATE-001",
                "duplicate-product-002",
                "duplicate-key-2",
                "trace-duplicate-2")))
                .isInstanceOf(DuplicateProductCodeException.class);

        assertThat(count("products")).isEqualTo(1);
        assertThat(count("product_admin_idempotency_keys")).isEqualTo(1);
        assertThat(auditOutcomes()).containsExactlyInAnyOrder(
                "SUCCESS:",
                "CONFLICT:DUPLICATE_PRODUCT_CODE");
    }

    @Test
    @Timeout(20)
    void concurrentProductCodeConstraintRaceReturnsStableApplicationFailure() throws Exception {
        List<Object> outcomes = saveConcurrently(
                ProductAggregate.createDraft(
                        null,
                        "PROD-RACE-CODE",
                        "race-code-product-a",
                        "Race Code A",
                        null,
                        null),
                ProductAggregate.createDraft(
                        null,
                        "PROD-RACE-CODE",
                        "race-code-product-b",
                        "Race Code B",
                        null,
                        null));

        assertOneSuccessAndFailure(outcomes, DuplicateProductCodeException.class);
        assertThat(count("products")).isEqualTo(1);
    }

    @Test
    @Timeout(20)
    void concurrentProductSlugConstraintRaceReturnsStableApplicationFailure() throws Exception {
        List<Object> outcomes = saveConcurrently(
                ProductAggregate.createDraft(
                        null,
                        "PROD-RACE-SLUG-A",
                        "race-shared-slug",
                        "Race Slug A",
                        null,
                        null),
                ProductAggregate.createDraft(
                        null,
                        "PROD-RACE-SLUG-B",
                        "race-shared-slug",
                        "Race Slug B",
                        null,
                        null));

        assertOneSuccessAndFailure(outcomes, DuplicateProductSlugException.class);
        assertThat(count("products")).isEqualTo(1);
    }

    @Test
    void adminSearchTrimsCaseAndTreatsLikeControlCharactersAsLiterals() {
        Instant updatedAt = INITIAL_TIME;
        UUID productText = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID productSku = UUID.fromString("00000000-0000-0000-0000-000000000012");
        UUID productLiteral = UUID.fromString("00000000-0000-0000-0000-000000000013");
        UUID productPlain = UUID.fromString("00000000-0000-0000-0000-000000000014");
        insertProduct(
                productText,
                "ALPHA-CODE",
                "target-slug",
                "Text Product",
                ProductStatus.DRAFT,
                updatedAt);
        insertProduct(
                productSku,
                "SKU-OWNER",
                "sku-owner",
                "Premium Phone",
                ProductStatus.DRAFT,
                updatedAt);
        insertVariant(productSku, "SKU-SPECIAL-001", updatedAt);
        insertProduct(
                productLiteral,
                "LITERAL-CONTROLS",
                "literal-controls",
                "Literal % _ \\ Product",
                ProductStatus.DRAFT,
                updatedAt);
        insertProduct(
                productPlain,
                "PLAIN-CONTROLS",
                "plain-controls",
                "Literal X Y Product",
                ProductStatus.DRAFT,
                updatedAt);

        assertProductIds(browse(null, "  alpha-code  ", 0, 20), productText);
        assertProductIds(browse(null, "TARGET-SLUG", 0, 20), productText);
        assertProductIds(browse(null, "premium PHONE", 0, 20), productSku);
        assertProductIds(browse(null, "sku-special", 0, 20), productSku);
        assertProductIds(browse(null, "%", 0, 20), productLiteral);
        assertProductIds(browse(null, "_", 0, 20), productLiteral);
        assertProductIds(browse(null, "\\", 0, 20), productLiteral);
    }

    @Test
    void adminStatusFilterIncludesOnlyRequestedLifecycleState() {
        UUID draft = UUID.fromString("00000000-0000-0000-0000-000000000021");
        UUID active = UUID.fromString("00000000-0000-0000-0000-000000000022");
        insertProduct(draft, "STATUS-DRAFT", "status-draft", "Draft", ProductStatus.DRAFT, INITIAL_TIME);
        insertProduct(active, "STATUS-ACTIVE", "status-active", "Active", ProductStatus.ACTIVE, INITIAL_TIME);

        assertProductIds(browse(ProductStatus.ACTIVE, null, 0, 20), active);
        assertProductIds(browse(ProductStatus.DRAFT, null, 0, 20), draft);
    }

    @Test
    void adminPagingUsesDeterministicOrderAndReportsBoundedMetadata() {
        UUID firstAtTie = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondAtTie = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID older = UUID.fromString("00000000-0000-0000-0000-000000000003");
        Instant newestTime = INITIAL_TIME.plusSeconds(60);
        insertProduct(firstAtTie, "PAGE-001", "page-001", "Page 001", ProductStatus.DRAFT, newestTime);
        insertProduct(secondAtTie, "PAGE-002", "page-002", "Page 002", ProductStatus.DRAFT, newestTime);
        insertProduct(older, "PAGE-003", "page-003", "Page 003", ProductStatus.DRAFT, INITIAL_TIME);

        AdminCatalogPageResult<AdminProductSummaryResult> firstPage = browse(null, null, 0, 2);
        AdminCatalogPageResult<AdminProductSummaryResult> secondPage = browse(null, null, 1, 2);

        assertProductIds(firstPage, firstAtTie, secondAtTie);
        assertThat(firstPage.page()).isEqualTo(new AdminPageMetadata(0, 2, 3, 2, true));
        assertProductIds(secondPage, older);
        assertThat(secondPage.page()).isEqualTo(new AdminPageMetadata(1, 2, 3, 2, false));

        assertThatThrownBy(() -> browse(null, null, -1, 20))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> browse(null, null, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> browse(null, null, 0, 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminReadReturnsEmptyPageAndStableMissingDetailOutcome() {
        AdminCatalogPageResult<AdminProductSummaryResult> empty =
                browse(ProductStatus.DRAFT, "does-not-exist", 0, 20);

        assertThat(empty.data()).isEmpty();
        assertThat(empty.page()).isEqualTo(new AdminPageMetadata(0, 20, 0, 0, false));
        UUID missingId = UUID.fromString("00000000-0000-0000-0000-000000000099");
        assertThatThrownBy(() -> viewAdminProductUseCase.view(new ViewAdminProductQuery(missingId)))
                .isInstanceOf(AdminProductNotFoundException.class);
    }

    private List<Object> saveConcurrently(ProductAggregate first, ProductAggregate second) throws Exception {
        return invokeConcurrently(
                () -> saveInTransaction(first),
                () -> saveInTransaction(second));
    }

    private ProductAggregate saveInTransaction(ProductAggregate product) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> saveAdminProductPort.saveDraft(product));
    }

    private static List<Object> invokeConcurrently(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> invokeTogether(first, ready, start)),
                    executor.submit(() -> invokeTogether(second, ready, start)));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return futures.stream().map(ProductAdminDraftPersistenceIT::futureOutcome).toList();
        } finally {
            executor.shutdownNow();
        }
    }

    private static Object invokeTogether(
            Callable<?> action,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test did not start in time");
        }
        return action.call();
    }

    private static Object futureOutcome(Future<?> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException exception) {
            return exception.getCause();
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent operation did not finish", exception);
        }
    }

    private static void assertOneSuccessAndFailure(
            List<Object> outcomes,
            Class<? extends RuntimeException> expectedFailure) {
        assertThat(outcomes).filteredOn(ProductAggregate.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(expectedFailure::isInstance).hasSize(1);
    }

    private AdminCatalogPageResult<AdminProductSummaryResult> browse(
            ProductStatus status,
            String q,
            int page,
            int size) {
        return browseAdminCatalogUseCase.browse(new BrowseAdminCatalogQuery(status, q, page, size));
    }

    private static void assertProductIds(
            AdminCatalogPageResult<AdminProductSummaryResult> result,
            UUID... expectedIds) {
        assertThat(result.data())
                .extracting(AdminProductSummaryResult::id)
                .containsExactly(expectedIds);
    }

    private void insertProduct(
            UUID id,
            String code,
            String slug,
            String name,
            ProductStatus status,
            Instant updatedAt) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO products (
                    id, code, slug, name, attributes, status, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, '{}'::jsonb, ?, ?, ?, 0)
                """,
                id,
                code,
                slug,
                name,
                status.name(),
                timestamp,
                timestamp);
    }

    private void insertVariant(UUID productId, String sku, Instant updatedAt) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO product_variants (
                    id, product_id, sku, name, attributes, base_price, currency, status,
                    sort_order, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, '{}'::jsonb, ?, 'VND', 'ACTIVE', 0, ?, ?, 0)
                """,
                UUID.randomUUID(),
                productId,
                sku,
                sku + " name",
                BigDecimal.ONE,
                timestamp,
                timestamp);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private List<String> auditOutcomes() {
        return jdbc.queryForList(
                "SELECT outcome || ':' || coalesce(error_code, '') FROM product_admin_audit_logs",
                String.class);
    }

    private static CreateProductDraftCommand command(
            String code,
            String slug,
            String idempotencyKey,
            String traceId) {
        return new CreateProductDraftCommand(
                new CatalogAdminActor("admin-1"),
                new TraceId(traceId),
                idempotencyKey,
                code,
                slug,
                code + " name",
                "Short",
                "Long");
    }

    @TestConfiguration
    static class TestClockConfiguration {

        @Bean
        @Primary
        Clock testProductAdminClock() {
            return TEST_CLOCK;
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;

        private MutableClock(Instant initial, ZoneId zone) {
            this(new AtomicReference<>(initial), zone);
        }

        private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId requestedZone) {
            return new MutableClock(current, requestedZone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }

        void reset(Instant instant) {
            current.set(instant);
        }

        void advance(Duration duration) {
            current.updateAndGet(value -> value.plus(duration));
        }
    }
}
