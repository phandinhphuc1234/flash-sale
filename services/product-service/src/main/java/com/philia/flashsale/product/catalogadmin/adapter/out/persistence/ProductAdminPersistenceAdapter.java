package com.philia.flashsale.product.catalogadmin.adapter.out.persistence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.application.port.out.AdminIdempotencyPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.CheckCatalogUniquenessPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadAdminProductPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadExistingCategoriesPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.RecordCatalogAdminAuditPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.SaveAdminProductPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.MaintainProductCompositionPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.ProductLifecyclePersistencePort;
import com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand;
import com.philia.flashsale.product.catalogadmin.application.result.MaintainProductCompositionResult;
import com.philia.flashsale.product.catalogadmin.application.result.MutationIdempotencyDecision;
import com.philia.flashsale.product.catalogadmin.application.query.BrowseAdminCatalogQuery;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateProductCodeException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateProductSlugException;
import com.philia.flashsale.product.catalogadmin.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminPageMetadata;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductCategoryResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductMediaResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductSummaryResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductVariantResult;
import com.philia.flashsale.product.catalogadmin.application.result.CreateDraftIdempotencyDecision;
import com.philia.flashsale.product.catalogadmin.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.catalogadmin.domain.AdminCommandName;
import com.philia.flashsale.product.catalogadmin.domain.ProductAggregate;
import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;
import org.hibernate.exception.ConstraintViolationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Repository
public class ProductAdminPersistenceAdapter implements
        LoadAdminProductPort,
        SaveAdminProductPort,
        CheckCatalogUniquenessPort,
        LoadExistingCategoriesPort,
        AdminIdempotencyPort,
        RecordCatalogAdminAuditPort,
        MaintainProductCompositionPort,
        ProductLifecyclePersistencePort {

    private static final Duration IDEMPOTENCY_REPLAY_WINDOW = Duration.ofDays(7);
    private static final String PRODUCT_CODE_CONSTRAINT = "uq_products_code";
    private static final String PRODUCT_SLUG_CONSTRAINT = "uq_products_slug";
    private static final String ADVISORY_LOCK_SQL =
            "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))";

    private final ProductAdminJpaRepository productRepository;
    private final ProductAdminVariantJpaRepository variantRepository;
    private final ProductAdminCategoryJpaRepository categoryRepository;
    private final ProductAdminMediaJpaRepository mediaRepository;
    private final ProductAdminIdempotencyJpaRepository idempotencyRepository;
    private final ProductAdminAuditJpaRepository auditRepository;
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;
    private final EntityManager entityManager;

    public ProductAdminPersistenceAdapter(
            ProductAdminJpaRepository productRepository,
            ProductAdminVariantJpaRepository variantRepository,
            ProductAdminCategoryJpaRepository categoryRepository,
            ProductAdminMediaJpaRepository mediaRepository,
            ProductAdminIdempotencyJpaRepository idempotencyRepository,
            ProductAdminAuditJpaRepository auditRepository,
            JdbcTemplate jdbcTemplate,
            Clock clock,
            EntityManager entityManager) {
        this.productRepository = productRepository;
        this.variantRepository = variantRepository;
        this.categoryRepository = categoryRepository;
        this.mediaRepository = mediaRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.auditRepository = auditRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Override
    // Admin search reads all operational statuses; shopper visibility filtering belongs to the public adapter.
    public AdminCatalogPageResult<AdminProductSummaryResult> browseAdminProducts(
            BrowseAdminCatalogQuery query) {
        Page<AdminProductJpaEntity> page = productRepository.searchAdminProducts(
                query.status() == null ? null : query.status().name(),
                escapeLikeLiteral(query.q()),
                PageRequest.of(query.page(), query.size()));
        return new AdminCatalogPageResult<>(
                page.getContent().stream()
                        .map(ProductAdminPersistenceAdapter::toSummary)
                        .toList(),
                new AdminPageMetadata(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.hasNext()));
    }

    @Override
    // Build an admin detail read model from owned tables without returning JPA entities to application code.
    public Optional<AdminProductDetailResult> loadAdminProduct(UUID productId) {
        return productRepository.findById(productId)
                .map(product -> new AdminProductDetailResult(
                        product.id(),
                        product.code(),
                        product.slug(),
                        product.name(),
                        product.shortDescription(),
                        product.description(),
                        product.status(),
                        product.publishedAt(),
                        product.version(),
                        variantRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                                .map(ProductAdminPersistenceAdapter::toVariant)
                                .toList(),
                        categoryRepository.findByProductIdOrderBySortOrderAscCategoryIdAsc(productId).stream()
                                .map(ProductAdminPersistenceAdapter::toCategory)
                                .toList(),
                        mediaRepository.findByProductIdOrderBySortOrderAscIdAsc(productId).stream()
                                .map(ProductAdminPersistenceAdapter::toMedia)
                                .toList()));
    }

    @Override
    public Optional<ProductAggregate> loadAdminAggregate(UUID productId) {
        return productRepository.findById(productId).map(AdminProductJpaEntity::toAggregate);
    }

    @Override
    public MaintainProductCompositionResult replaceComposition(MaintainProductCompositionCommand command) {
        AdminProductJpaEntity product = productRepository.findById(command.productId())
                .orElseThrow(() -> new com.philia.flashsale.product.catalogadmin.application.exception.AdminProductNotFoundException(command.productId()));
        if (product.version() != command.expectedVersion()) {
            throw new com.philia.flashsale.product.catalogadmin.application.exception.StaleProductVersionException(
                    command.productId(), product.version());
        }
        product.updateContent(command.name().trim(), normalize(command.shortDescription()), normalize(command.description()));
        try {
            // Composition owns child tables too; force the aggregate version to advance even when only a child changed.
            entityManager.lock(product, LockModeType.OPTIMISTIC_FORCE_INCREMENT);
            productRepository.saveAndFlush(product);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new com.philia.flashsale.product.catalogadmin.application.exception.StaleProductVersionException(
                    command.productId(), command.expectedVersion() + 1);
        }

        var existing = variantRepository.findByProductIdOrderBySortOrderAscIdAsc(command.productId());
        var requestedIds = command.variants().stream().map(MaintainProductCompositionCommand.VariantInput::id)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        existing.stream().filter(value -> !requestedIds.contains(value.id())).forEach(AdminProductVariantJpaEntity::deactivate);
        variantRepository.saveAll(existing);
        for (MaintainProductCompositionCommand.VariantInput input : command.variants()) {
            if (input.id() == null) {
                variantRepository.save(AdminProductVariantJpaEntity.from(command.productId(), input));
            } else {
                existing.stream().filter(value -> value.id().equals(input.id())).findFirst()
                        .ifPresent(value -> value.update(input));
            }
        }
        variantRepository.flush();
        categoryRepository.deleteByProductId(command.productId());
        categoryRepository.flush();
        categoryRepository.saveAll(command.categories().stream()
                .map(value -> AdminProductCategoryJpaEntity.from(command.productId(), value)).toList());
        categoryRepository.flush();
        mediaRepository.deleteByProductId(command.productId());
        mediaRepository.flush();
        mediaRepository.saveAll(command.media().stream()
                .map(value -> AdminProductMediaJpaEntity.from(command.productId(), value)).toList());
        mediaRepository.flush();
        // OPTIMISTIC_FORCE_INCREMENT schedules the version bump at flush/commit; the managed field can still
        // expose the pre-bump value, so return the deterministic next aggregate version to the caller.
        return new MaintainProductCompositionResult(command.productId(), product.version() + 1);
    }

    @Override
    public long updateLifecycle(UUID productId, long expectedVersion, ProductStatus target, Instant publishedAt) {
        AdminProductJpaEntity product = productRepository.findById(productId)
                .orElseThrow(() -> new com.philia.flashsale.product.catalogadmin.application.exception.AdminProductNotFoundException(productId));
        if (product.version() != expectedVersion) {
            throw new com.philia.flashsale.product.catalogadmin.application.exception.StaleProductVersionException(productId, product.version());
        }
        product.updateLifecycle(target, publishedAt);
        try {
            return productRepository.saveAndFlush(product).version();
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new com.philia.flashsale.product.catalogadmin.application.exception.StaleProductVersionException(
                    productId, expectedVersion + 1);
        }
    }

    @Override
    // Persist only the aggregate root for draft creation; variants/categories/media are owned by later use cases.
    public ProductAggregate saveDraft(ProductAggregate product) {
        try {
            return productRepository.saveAndFlush(AdminProductJpaEntity.from(product)).toAggregate();
        } catch (DataIntegrityViolationException exception) {
            throw translateProductUniquenessViolation(exception, product);
        }
    }

    @Override
    public boolean productCodeExists(String code) {
        return productRepository.existsByCode(code);
    }

    @Override
    public boolean productSlugExists(String slug) {
        return productRepository.existsBySlug(slug);
    }

    @Override
    public boolean variantSkuExists(String sku) {
        return variantRepository.existsBySku(sku);
    }

    @Override
    public boolean barcodeExists(String barcode) {
        return barcode != null && variantRepository.existsByBarcode(barcode);
    }

    @Override
    public Set<UUID> existingCategoryIds(Collection<UUID> requestedCategoryIds) {
        if (requestedCategoryIds == null || requestedCategoryIds.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(categoryRepository.findExistingCategoryIds(requestedCategoryIds));
    }

    @Override
    public CreateDraftIdempotencyDecision resolveCreateDraft(
            String actorId,
            String idempotencyKey,
            String requestHash) {
        requireActiveTransaction("Create-draft idempotency resolution");
        acquireCreateDraftAdvisoryLock(actorId, idempotencyKey);

        Instant now = clock.instant();
        Optional<AdminIdempotencyJpaEntity> stored =
                idempotencyRepository.findByActorIdAndIdempotencyKey(actorId, idempotencyKey);
        if (stored.isEmpty()) {
            return CreateDraftIdempotencyDecision.fresh();
        }

        AdminIdempotencyJpaEntity record = stored.orElseThrow();
        if (!record.expiresAt().isAfter(now)) {
            // The advisory lock makes delete-and-reuse atomic for this logical actor/key pair.
            idempotencyRepository.delete(record);
            idempotencyRepository.flush();
            return CreateDraftIdempotencyDecision.fresh();
        }
        if (requestHash.equals(record.requestHash())) {
            return CreateDraftIdempotencyDecision.replay(toCreateDraftResult(record.responseBody()));
        }
        return CreateDraftIdempotencyDecision.conflict();
    }

    @Override
    public void storeCreateDraftOutcome(
            String actorId,
            String idempotencyKey,
            AdminCommandName commandName,
            String requestHash,
            UUID productId,
            CreateProductDraftResult result) {
        requireActiveTransaction("Create-draft idempotency outcome storage");
        Instant now = clock.instant();
        // Store the response body so a retry can return the original outcome without touching product tables again.
        idempotencyRepository.saveAndFlush(new AdminIdempotencyJpaEntity(
                actorId,
                idempotencyKey,
                commandName,
                requestHash,
                productId,
                201,
                Map.of(
                        "id", result.id().toString(),
                        "status", result.status().name(),
                        "version", result.version()),
                now,
                now.plus(IDEMPOTENCY_REPLAY_WINDOW)));
    }

    @Override
    public MutationIdempotencyDecision resolveMutation(String actorId, String idempotencyKey, String requestHash) {
        requireActiveTransaction("Lifecycle idempotency resolution");
        acquireCreateDraftAdvisoryLock(actorId, idempotencyKey);
        Optional<AdminIdempotencyJpaEntity> stored = idempotencyRepository.findByActorIdAndIdempotencyKey(actorId, idempotencyKey);
        if (stored.isEmpty()) return MutationIdempotencyDecision.fresh();
        AdminIdempotencyJpaEntity record = stored.orElseThrow();
        if (!record.expiresAt().isAfter(clock.instant())) {
            idempotencyRepository.delete(record);
            idempotencyRepository.flush();
            return MutationIdempotencyDecision.fresh();
        }
        if (requestHash.equals(record.requestHash())) {
            return MutationIdempotencyDecision.replay(record.targetProductId(), record.version(), record.status());
        }
        return MutationIdempotencyDecision.conflict();
    }

    @Override
    public void storeMutationOutcome(String actorId, String idempotencyKey, AdminCommandName commandName,
            String requestHash, UUID productId, int httpStatus, String status, long version) {
        requireActiveTransaction("Lifecycle idempotency outcome storage");
        Instant now = clock.instant();
        idempotencyRepository.saveAndFlush(new AdminIdempotencyJpaEntity(actorId, idempotencyKey, commandName,
                requestHash, productId, httpStatus, Map.of("id", productId.toString(), "status", status, "version", version),
                now, now.plus(IDEMPOTENCY_REPLAY_WINDOW)));
    }

    @Override
    public void record(
            String actorId,
            String traceId,
            AdminCommandName commandName,
            UUID targetProductId,
            String outcome,
            String errorCode,
            Long productVersion) {
        // Audit is append-only operational evidence for privileged catalog commands.
        auditRepository.save(new AdminAuditJpaEntity(
                actorId,
                traceId,
                commandName,
                targetProductId,
                outcome,
                errorCode,
                productVersion,
                clock.instant()));
    }

    private void acquireCreateDraftAdvisoryLock(String actorId, String idempotencyKey) {
        String lockIdentity = actorId.length() + ":" + actorId + ":" + idempotencyKey;
        jdbcTemplate.execute(
                (PreparedStatementCreator) connection -> {
                    var statement = connection.prepareStatement(ADVISORY_LOCK_SQL);
                    statement.setString(1, lockIdentity);
                    return statement;
                },
                (PreparedStatementCallback<Void>) statement -> {
                    statement.execute();
                    return null;
                });
    }

    private static RuntimeException translateProductUniquenessViolation(
            DataIntegrityViolationException failure,
            ProductAggregate product) {
        String constraintName = findConstraintName(failure);
        String failureText = failure.toString().toLowerCase(Locale.ROOT);
        if (PRODUCT_CODE_CONSTRAINT.equalsIgnoreCase(constraintName)
                || failureText.contains(PRODUCT_CODE_CONSTRAINT)) {
            return new DuplicateProductCodeException(product.code());
        }
        if (PRODUCT_SLUG_CONSTRAINT.equalsIgnoreCase(constraintName)
                || failureText.contains(PRODUCT_SLUG_CONSTRAINT)) {
            return new DuplicateProductSlugException(product.slug());
        }
        return failure;
    }

    private static String findConstraintName(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }

    private static void requireActiveTransaction(String operation) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(operation + " requires an active transaction");
        }
    }

    private static AdminProductSummaryResult toSummary(AdminProductJpaEntity product) {
        return new AdminProductSummaryResult(
                product.id(),
                product.code(),
                product.slug(),
                product.name(),
                product.status(),
                product.publishedAt(),
                product.version());
    }

    private static String escapeLikeLiteral(String value) {
        if (value == null) {
            return null;
        }
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static AdminProductVariantResult toVariant(AdminProductVariantJpaEntity variant) {
        return new AdminProductVariantResult(
                variant.id(),
                variant.sku(),
                variant.barcode(),
                variant.name(),
                variant.basePrice(),
                variant.currency(),
                variant.status(),
                variant.sortOrder());
    }

    private static AdminProductCategoryResult toCategory(AdminProductCategoryJpaEntity category) {
        return new AdminProductCategoryResult(
                category.categoryId(),
                category.primary(),
                category.sortOrder());
    }

    private static AdminProductMediaResult toMedia(AdminProductMediaJpaEntity media) {
        return new AdminProductMediaResult(
                media.id(),
                media.variantId(),
                media.mediaType(),
                media.url(),
                media.altText(),
                media.sortOrder(),
                media.status());
    }

    private static CreateProductDraftResult toCreateDraftResult(Map<String, Object> body) {
        Number version = (Number) body.get("version");
        return new CreateProductDraftResult(
                UUID.fromString((String) body.get("id")),
                ProductStatus.valueOf((String) body.get("status")),
                version.longValue(),
                true);
    }
}
