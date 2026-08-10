package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProductAdminDraftHttpTests {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("product_db")
                    .withUsername("product")
                    .withPassword("product");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void clearBusinessData() {
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
    void createDraftCanBeListedAndViewedByAdminButNotByShopperCatalog() throws Exception {
        String request = """
                {
                  "code": "PROD-ADMIN-001",
                  "slug": "admin-product-001",
                  "name": "Admin Product 001",
                  "shortDescription": "Short",
                  "description": "Long"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("Idempotency-Key", "create-admin-product-001")
                        .header("X-Trace-Id", "trace-admin-create-001")
                        .header("X-Actor-Id", "caller-supplied-actor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", Matchers.containsString("/api/v1/admin/catalog/products/")))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String productId = response.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        assertEquals(
                "admin-1",
                jdbc.queryForObject(
                        "SELECT actor_id FROM product_admin_audit_logs WHERE trace_id = ?",
                        String.class,
                        "trace-admin-create-001"));

        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-admin-list-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data", hasSize(1)))
                .andExpect(jsonPath("$.data.data[0].code").value("PROD-ADMIN-001"))
                .andExpect(jsonPath("$.data.data[0].status").value("DRAFT"));

        mockMvc.perform(get("/api/v1/admin/catalog/products/{productId}", productId)
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-admin-detail-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.variants", hasSize(0)))
                .andExpect(jsonPath("$.data.categories", hasSize(0)))
                .andExpect(jsonPath("$.data.media", hasSize(0)));

        mockMvc.perform(get("/api/v1/catalog/products/admin-product-001"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void compositionAndLifecycleApisPrepareAndTransitionSellableVariant() throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("Idempotency-Key", "create-composition-1")
                        .header("X-Trace-Id", "trace-composition-create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequest("PROD-COMPOSE-001", "compose-001", "Compose Product", "Short")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String productId = created.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

        String composition = """
                {
                  "name": "Compose Product",
                  "shortDescription": "Short",
                  "variants": [{
                    "sku": "COMPOSE-SKU-001",
                    "name": "Default",
                    "basePrice": 1000,
                    "currency": "VND",
                    "status": "ACTIVE",
                    "sortOrder": 0
                  }],
                  "categories": [],
                  "media": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders
                        .put("/api/v1/admin/catalog/products/{productId}/composition", productId)
                        .with(catalogAdmin())
                        .header("If-Match", "0")
                        .header("X-Trace-Id", "trace-composition-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(composition))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(productId))
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(post("/api/v1/admin/catalog/products/{productId}/publish", productId)
                        .with(catalogAdmin())
                        .header("If-Match", "1")
                        .header("Idempotency-Key", "publish-composition-1")
                        .header("X-Trace-Id", "trace-publish-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(2));

        mockMvc.perform(post("/api/v1/admin/catalog/products/{productId}/deactivate", productId)
                        .with(catalogAdmin())
                        .header("If-Match", "2")
                        .header("Idempotency-Key", "deactivate-composition-1")
                        .header("X-Trace-Id", "trace-deactivate-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.version").value(3));

        mockMvc.perform(post("/api/v1/admin/catalog/products/{productId}/publish", productId)
                        .with(catalogAdmin())
                        .header("If-Match", "3")
                        .header("Idempotency-Key", "reactivate-composition-1")
                        .header("X-Trace-Id", "trace-reactivate-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.version").value(4));

        mockMvc.perform(post("/api/v1/admin/catalog/products/{productId}/archive", productId)
                        .with(catalogAdmin())
                        .header("If-Match", "4")
                        .header("Idempotency-Key", "archive-composition-1")
                        .header("X-Trace-Id", "trace-archive-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.version").value(5));
    }

    @Test
    void createDraftRejectsMissingMalformedBlankAndOversizedInput() throws Exception {
        String validRequest = """
                {
                  "code": "PROD-VALID",
                  "slug": "product-valid",
                  "name": "Product Valid"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-missing-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("Idempotency-Key", "missing-trace")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"));

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("Idempotency-Key", "malformed-json")
                        .header("X-Trace-Id", "trace-malformed-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("Idempotency-Key", "blank-product-name")
                        .header("X-Trace-Id", "trace-blank-product-name")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "PROD-BLANK",
                                  "slug": "product-blank",
                                  "name": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        String[] oversizedRequests = {
                createRequest("C".repeat(65), "valid-slug", "Valid name", null),
                createRequest("VALID-CODE", "s".repeat(201), "Valid name", null),
                createRequest("VALID-CODE", "valid-slug", "N".repeat(256), null),
                createRequest("VALID-CODE", "valid-slug", "Valid name", "S".repeat(501))
        };
        for (int index = 0; index < oversizedRequests.length; index++) {
            String traceId = "trace-oversized-" + index;
            mockMvc.perform(post("/api/v1/admin/catalog/products")
                            .with(catalogAdmin())
                            .header("Idempotency-Key", "oversized-" + index)
                            .header("X-Trace-Id", traceId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(oversizedRequests[index]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"))
                    .andExpect(jsonPath("$.traceId").doesNotExist());
        }

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("Idempotency-Key", "K".repeat(129))
                        .header("X-Trace-Id", "trace-oversized-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"));

        assertEquals(
                0,
                jdbc.queryForObject("SELECT count(*) FROM products", Integer.class).intValue());
    }

    @Test
    void adminReadsRequireValidTraceIdentifiersAndEnums() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"));

        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"));

        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "T".repeat(129)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"));

        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-invalid-status")
                        .queryParam("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/catalog/products/not-a-uuid")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-invalid-id"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/catalog/products/{productId}", UUID.randomUUID())
                        .with(catalogAdmin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"));
    }

    @Test
    void adminCreateRequiresAuthenticationAndCatalogAdminAuthority() throws Exception {
        String request = """
                {
                  "code": "PROD-ADMIN-002",
                  "slug": "admin-product-002",
                  "name": "Admin Product 002"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Idempotency-Key", "create-admin-product-002")
                        .header("X-Trace-Id", "trace-admin-create-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("CATALOG_VIEWER")))
                        .header("Idempotency-Key", "create-admin-product-002")
                        .header("X-Trace-Id", "trace-admin-create-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("CATALOG_ADMIN_REQUIRED"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        assertEquals(
                0,
                jdbc.queryForObject("SELECT count(*) FROM products", Integer.class).intValue());
    }

    @Test
    void concurrentCreateWithDuplicateCodeReturnsOneCreatedAndOneStableConflict() throws Exception {
        MvcResult[] results = runConcurrentCreates(
                createRequest("RACE-CODE", "race-code-first", "Race Code First", null),
                "race-code-key-first",
                "trace-race-code-first",
                createRequest("RACE-CODE", "race-code-second", "Race Code Second", null),
                "race-code-key-second",
                "trace-race-code-second");

        assertConcurrentDuplicateOutcome(results, "DUPLICATE_PRODUCT_CODE");
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM products WHERE code = 'RACE-CODE'",
                        Integer.class).intValue());
        assertDuplicateAuditEvidence("DUPLICATE_PRODUCT_CODE");
    }

    @Test
    void concurrentCreateWithDuplicateSlugReturnsOneCreatedAndOneStableConflict() throws Exception {
        MvcResult[] results = runConcurrentCreates(
                createRequest("RACE-SLUG-FIRST", "race-shared-slug", "Race Slug First", null),
                "race-slug-key-first",
                "trace-race-slug-first",
                createRequest("RACE-SLUG-SECOND", "race-shared-slug", "Race Slug Second", null),
                "race-slug-key-second",
                "trace-race-slug-second");

        assertConcurrentDuplicateOutcome(results, "DUPLICATE_PRODUCT_SLUG");
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM products WHERE slug = 'race-shared-slug'",
                        Integer.class).intValue());
        assertDuplicateAuditEvidence("DUPLICATE_PRODUCT_SLUG");
    }

    @Test
    void adminBrowseAppliesStatusAndCaseInsensitiveLiteralSearch() throws Exception {
        UUID draftId = uuid(1);
        UUID activeId = uuid(2);
        UUID inactiveId = uuid(3);
        UUID archivedId = uuid(4);
        Instant updatedAt = Instant.parse("2026-07-20T10:00:00Z");
        insertProduct(draftId, "STATE-DRAFT", "state-draft", "State Draft", "DRAFT", updatedAt);
        insertProduct(activeId, "STATE-ACTIVE", "state-active", "State Active", "ACTIVE", updatedAt);
        insertProduct(inactiveId, "STATE-INACTIVE", "state-inactive", "State Inactive", "INACTIVE", updatedAt);
        insertProduct(archivedId, "STATE-ARCHIVED", "state-archived", "State Archived", "ARCHIVED", updatedAt);

        List.of(
                        new StatusExpectation("DRAFT", draftId),
                        new StatusExpectation("ACTIVE", activeId),
                        new StatusExpectation("INACTIVE", inactiveId),
                        new StatusExpectation("ARCHIVED", archivedId))
                .forEach(expectation -> assertStatusFilter(expectation.status(), expectation.productId()));

        UUID codeId = uuid(11);
        UUID slugId = uuid(12);
        UUID nameId = uuid(13);
        UUID skuId = uuid(14);
        UUID percentId = uuid(15);
        UUID underscoreId = uuid(16);
        insertProduct(codeId, "CodeNeedle", "plain-code", "Plain Code", "DRAFT", updatedAt);
        insertProduct(slugId, "PLAIN-SLUG", "SlugNeedle", "Plain Slug", "DRAFT", updatedAt);
        insertProduct(nameId, "PLAIN-NAME", "plain-name", "NameNeedle", "DRAFT", updatedAt);
        insertProduct(skuId, "PLAIN-SKU", "plain-sku", "Plain SKU", "DRAFT", updatedAt);
        insertVariant(skuId, "Special-SkU-Needle");
        insertProduct(percentId, "PERCENT%CODE", "percent-code", "Percent Code", "DRAFT", updatedAt);
        insertProduct(underscoreId, "UNDER_SCORE", "under-score", "Under Score", "DRAFT", updatedAt);

        assertSearchReturnsOnly("codeneedle", codeId);
        assertSearchReturnsOnly("slugneedle", slugId);
        assertSearchReturnsOnly("nameneedle", nameId);
        assertSearchReturnsOnly("sku-needle", skuId);
        assertSearchReturnsOnly("%", percentId);
        assertSearchReturnsOnly("_", underscoreId);
    }

    @Test
    void adminBrowseUsesBoundedDeterministicPaging() throws Exception {
        Instant newest = Instant.parse("2026-07-20T10:00:00Z");
        Instant older = Instant.parse("2026-07-20T09:00:00Z");
        UUID firstId = uuid(1);
        UUID secondId = uuid(2);
        UUID olderId = uuid(3);
        insertProduct(firstId, "PAGE-1", "page-1", "Page 1", "DRAFT", newest);
        insertProduct(secondId, "PAGE-2", "page-2", "Page 2", "DRAFT", newest);
        insertProduct(olderId, "PAGE-3", "page-3", "Page 3", "DRAFT", older);

        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-page-0")
                        .queryParam("page", "0")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data", hasSize(2)))
                .andExpect(jsonPath("$.data.data[0].id").value(firstId.toString()))
                .andExpect(jsonPath("$.data.data[1].id").value(secondId.toString()))
                .andExpect(jsonPath("$.data.page.number").value(0))
                .andExpect(jsonPath("$.data.page.size").value(2))
                .andExpect(jsonPath("$.data.page.totalElements").value(3))
                .andExpect(jsonPath("$.data.page.totalPages").value(2))
                .andExpect(jsonPath("$.data.page.hasNext").value(true));

        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-page-1")
                        .queryParam("page", "1")
                        .queryParam("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data", hasSize(1)))
                .andExpect(jsonPath("$.data.data[0].id").value(olderId.toString()))
                .andExpect(jsonPath("$.data.page.hasNext").value(false));
    }

    @Test
    void adminBrowseAndDetailReturnStableEmptyMissingAndInvalidPagingOutcomes() throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-empty-list"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data", hasSize(0)))
                .andExpect(jsonPath("$.data.page.number").value(0))
                .andExpect(jsonPath("$.data.page.size").value(20))
                .andExpect(jsonPath("$.data.page.totalElements").value(0))
                .andExpect(jsonPath("$.data.page.totalPages").value(0))
                .andExpect(jsonPath("$.data.page.hasNext").value(false));

        UUID missingId = uuid(999);
        mockMvc.perform(get("/api/v1/admin/catalog/products/{productId}", missingId)
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-missing-detail"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").doesNotExist());

        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-page-max")
                        .queryParam("size", "100"))
                .andExpect(status().isOk());

        assertInvalidPaging("page", "-1", "trace-invalid-page");
        assertInvalidPaging("size", "0", "trace-size-zero");
        assertInvalidPaging("size", "101", "trace-size-over-max");
    }

    private MvcResult[] runConcurrentCreates(
            String firstRequest,
            String firstIdempotencyKey,
            String firstTraceId,
            String secondRequest,
            String secondIdempotencyKey,
            String secondTraceId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<MvcResult> first = executor.submit(() -> performConcurrentCreate(
                    firstRequest, firstIdempotencyKey, firstTraceId, ready, start));
            Future<MvcResult> second = executor.submit(() -> performConcurrentCreate(
                    secondRequest, secondIdempotencyKey, secondTraceId, ready, start));

            assertTrue(ready.await(10, TimeUnit.SECONDS), "Both HTTP requests must reach the start gate");
            start.countDown();
            return new MvcResult[] {
                    first.get(30, TimeUnit.SECONDS),
                    second.get(30, TimeUnit.SECONDS)
            };
        }
    }

    private MvcResult performConcurrentCreate(
            String request,
            String idempotencyKey,
            String traceId,
            CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent create start gate timed out");
        }
        return mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Trace-Id", traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andReturn();
    }

    private void assertConcurrentDuplicateOutcome(MvcResult[] results, String expectedErrorCode)
            throws Exception {
        List<Integer> statuses = List.of(
                results[0].getResponse().getStatus(),
                results[1].getResponse().getStatus());
        assertEquals(1, statuses.stream().filter(status -> status == 201).count());
        assertEquals(1, statuses.stream().filter(status -> status == 409).count());

        MvcResult conflict = results[0].getResponse().getStatus() == 409 ? results[0] : results[1];
        assertEquals(
                expectedErrorCode,
                objectMapper.readTree(conflict.getResponse().getContentAsByteArray())
                        .path("errorCode")
                        .asText());
    }

    private void assertDuplicateAuditEvidence(String errorCode) {
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT count(*) FROM product_admin_audit_logs WHERE outcome = 'SUCCESS'",
                        Integer.class).intValue());
        assertEquals(
                1,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM product_admin_audit_logs
                        WHERE outcome = 'CONFLICT' AND error_code = ?
                        """,
                        Integer.class,
                        errorCode).intValue());
    }

    private void assertStatusFilter(String status, UUID expectedProductId) {
        try {
            mockMvc.perform(get("/api/v1/admin/catalog/products")
                            .with(catalogAdmin())
                            .header("X-Trace-Id", "trace-status-" + status.toLowerCase())
                            .queryParam("status", status))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.data", hasSize(1)))
                    .andExpect(jsonPath("$.data.data[0].id").value(expectedProductId.toString()))
                    .andExpect(jsonPath("$.data.data[0].status").value(status));
        } catch (Exception exception) {
            throw new AssertionError("Status filter failed for " + status, exception);
        }
    }

    private void assertSearchReturnsOnly(String query, UUID expectedProductId) throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", "trace-search-" + expectedProductId)
                        .queryParam("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data", hasSize(1)))
                .andExpect(jsonPath("$.data.data[0].id").value(expectedProductId.toString()));
    }

    private void assertInvalidPaging(String parameter, String value, String traceId) throws Exception {
        mockMvc.perform(get("/api/v1/admin/catalog/products")
                        .with(catalogAdmin())
                        .header("X-Trace-Id", traceId)
                        .queryParam(parameter, value))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ADMIN_REQUEST"))
                .andExpect(jsonPath("$.traceId").doesNotExist());
    }

    private void insertProduct(
            UUID id,
            String code,
            String slug,
            String name,
            String status,
            Instant updatedAt) {
        Timestamp timestamp = Timestamp.from(updatedAt);
        jdbc.update(
                """
                INSERT INTO products (
                    id, code, slug, name, status, published_at, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                id,
                code,
                slug,
                name,
                status,
                "ACTIVE".equals(status) ? timestamp : null,
                timestamp,
                timestamp);
    }

    private void insertVariant(UUID productId, String sku) {
        jdbc.update(
                """
                INSERT INTO product_variants (
                    id, product_id, sku, name, base_price, currency, status, sort_order
                ) VALUES (?, ?, ?, ?, ?, 'VND', 'ACTIVE', 0)
                """,
                UUID.randomUUID(),
                productId,
                sku,
                sku,
                BigDecimal.ZERO);
    }

    private static UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }

    private record StatusExpectation(String status, UUID productId) {
    }

    private static String createRequest(
            String code,
            String slug,
            String name,
            String shortDescription) {
        String shortDescriptionJson = shortDescription == null
                ? "null"
                : "\"" + shortDescription + "\"";
        return """
                {
                  "code": "%s",
                  "slug": "%s",
                  "name": "%s",
                  "shortDescription": %s
                }
                """.formatted(code, slug, name, shortDescriptionJson);
    }

    private static RequestPostProcessor catalogAdmin() {
        return jwt()
                .jwt(jwt -> jwt.subject("admin-1"))
                .authorities(new SimpleGrantedAuthority("CATALOG_ADMIN"));
    }
}
