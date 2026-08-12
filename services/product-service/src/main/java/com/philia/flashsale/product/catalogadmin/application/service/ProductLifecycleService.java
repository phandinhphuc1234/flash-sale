package com.philia.flashsale.product.catalogadmin.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.application.command.ChangeProductLifecycleCommand;
import com.philia.flashsale.product.catalogadmin.application.exception.AdminProductNotFoundException;
import com.philia.flashsale.product.catalogadmin.application.exception.IdempotencyKeyReusedException;
import com.philia.flashsale.product.catalogadmin.application.port.in.ArchiveProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.DeactivateProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.PublishProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.in.ReactivateProductUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.out.AdminIdempotencyPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.LoadAdminProductPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.ProductLifecyclePersistencePort;
import com.philia.flashsale.product.catalogadmin.application.port.out.RecordCatalogAdminAuditPort;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.catalogadmin.application.result.MutationIdempotencyDecision;
import com.philia.flashsale.product.catalogadmin.application.result.ProductLifecycleResult;
import com.philia.flashsale.product.catalogadmin.domain.ProductAggregate;
import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;
import com.philia.flashsale.product.catalogadmin.domain.VariantStatus;
import com.philia.flashsale.product.catalogadmin.domain.policy.ProductLifecyclePolicy;
import com.philia.flashsale.product.catalogadmin.domain.exception.PublicationPrerequisiteException;

public final class ProductLifecycleService implements PublishProductUseCase, DeactivateProductUseCase,
        ReactivateProductUseCase, ArchiveProductUseCase {
    private final LoadAdminProductPort loader;
    private final ProductLifecyclePersistencePort persistence;
    private final AdminIdempotencyPort idempotency;
    private final RecordCatalogAdminAuditPort audit;
    private final ProductLifecyclePolicy policy = new ProductLifecyclePolicy();
    private final Clock clock;

    public ProductLifecycleService(LoadAdminProductPort loader, ProductLifecyclePersistencePort persistence,
            AdminIdempotencyPort idempotency, RecordCatalogAdminAuditPort audit, Clock clock) {
        this.loader = loader;
        this.persistence = persistence;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    @Override public ProductLifecycleResult publish(ChangeProductLifecycleCommand command) { return change(command, ProductStatus.ACTIVE); }
    @Override public ProductLifecycleResult deactivate(ChangeProductLifecycleCommand command) { return change(command, ProductStatus.INACTIVE); }
    @Override public ProductLifecycleResult reactivate(ChangeProductLifecycleCommand command) { return change(command, ProductStatus.ACTIVE); }
    @Override public ProductLifecycleResult archive(ChangeProductLifecycleCommand command) { return change(command, ProductStatus.ARCHIVED); }

    private ProductLifecycleResult change(ChangeProductLifecycleCommand command, ProductStatus target) {
        MutationIdempotencyDecision decision = idempotency.resolveMutation(command.actor().actorId(), command.idempotencyKey(), command.requestHash());
        if (decision.type() == MutationIdempotencyDecision.Type.CONFLICT) {
            audit.record(command.actor().actorId(), command.traceId().value(), command.commandName(), command.productId(),
                    "CONFLICT", "IDEMPOTENCY_KEY_REUSED", null);
            throw new IdempotencyKeyReusedException();
        }
        if (decision.type() == MutationIdempotencyDecision.Type.REPLAY) {
            ProductLifecycleResult result = new ProductLifecycleResult(decision.productId(), ProductStatus.valueOf(decision.status()),
                    loader.loadAdminProduct(decision.productId()).map(AdminProductDetailResult::publishedAt).orElse(null), decision.version(), true);
            audit.record(command.actor().actorId(), command.traceId().value(), command.commandName(), command.productId(),
                    "REPLAYED", null, result.version());
            return result;
        }
        AdminProductDetailResult detail = loader.loadAdminProduct(command.productId())
                .orElseThrow(() -> new AdminProductNotFoundException(command.productId()));
        ProductAggregate product = ProductAggregate.rehydrate(detail.id(), detail.code(), detail.slug(), detail.name(),
                detail.shortDescription(), detail.description(), detail.status(), detail.publishedAt(), detail.version());
        policy.requireAllowedTransition(product.status(), target);
        if (target == ProductStatus.ACTIVE && (product.status() == ProductStatus.DRAFT || product.status() == ProductStatus.INACTIVE)) {
            boolean hasPricedActiveVariant = detail.variants().stream().anyMatch(v -> v.status() == VariantStatus.ACTIVE
                    && v.basePrice() != null && v.basePrice().signum() >= 0 && "VND".equals(v.currency()));
            if (!hasPricedActiveVariant) throw new PublicationPrerequisiteException("At least one active Variant with valid VND base price is required");
        }
        Instant publishedAt = target == ProductStatus.ACTIVE ? (detail.publishedAt() == null ? clock.instant() : detail.publishedAt()) : detail.publishedAt();
        long version = persistence.updateLifecycle(command.productId(), command.expectedVersion(), target, publishedAt);
        idempotency.storeMutationOutcome(command.actor().actorId(), command.idempotencyKey(), command.commandName(), command.requestHash(),
                command.productId(), 200, target.name(), version);
        audit.record(command.actor().actorId(), command.traceId().value(), command.commandName(), command.productId(),
                "SUCCESS", null, version);
        return new ProductLifecycleResult(command.productId(), target, publishedAt, version, false);
    }
}
