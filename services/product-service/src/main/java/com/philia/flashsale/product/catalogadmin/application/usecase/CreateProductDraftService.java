package com.philia.flashsale.product.catalogadmin.application.usecase;

import com.philia.flashsale.product.catalogadmin.application.command.CreateProductDraftCommand;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateProductCodeException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateProductSlugException;
import com.philia.flashsale.product.catalogadmin.application.exception.IdempotencyKeyReusedException;
import com.philia.flashsale.product.catalogadmin.application.port.in.CreateProductDraftUseCase;
import com.philia.flashsale.product.catalogadmin.application.port.out.AdminIdempotencyPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.CheckCatalogUniquenessPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.RecordCatalogAdminAuditPort;
import com.philia.flashsale.product.catalogadmin.application.port.out.SaveAdminProductPort;
import com.philia.flashsale.product.catalogadmin.application.result.CreateDraftIdempotencyDecision;
import com.philia.flashsale.product.catalogadmin.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.catalogadmin.domain.ProductAggregate;

public final class CreateProductDraftService implements CreateProductDraftUseCase {

    private final CheckCatalogUniquenessPort uniquenessPort;
    private final SaveAdminProductPort saveAdminProductPort;
    private final AdminIdempotencyPort idempotencyPort;
    private final RecordCatalogAdminAuditPort auditPort;

    public CreateProductDraftService(
            CheckCatalogUniquenessPort uniquenessPort,
            SaveAdminProductPort saveAdminProductPort,
            AdminIdempotencyPort idempotencyPort,
            RecordCatalogAdminAuditPort auditPort) {
        this.uniquenessPort = uniquenessPort;
        this.saveAdminProductPort = saveAdminProductPort;
        this.idempotencyPort = idempotencyPort;
        this.auditPort = auditPort;
    }

    @Override
    // Check idempotency first so an identical retry replays the original result instead of creating another draft.
    public CreateProductDraftResult createDraft(CreateProductDraftCommand command) {
        String requestHash = command.requestHash();
        String actorId = command.actor().actorId();
        CreateDraftIdempotencyDecision decision = idempotencyPort.resolveCreateDraft(
                actorId,
                command.idempotencyKey(),
                requestHash);

        return switch (decision.type()) {
            case REPLAY -> replayed(decision.replayResult());
            case CONFLICT -> throw new IdempotencyKeyReusedException();
            case FRESH -> createNewDraft(command, requestHash, actorId);
        };
    }

    private CreateProductDraftResult createNewDraft(
            CreateProductDraftCommand command,
            String requestHash,
            String actorId) {
        if (uniquenessPort.productCodeExists(command.code())) {
            throw new DuplicateProductCodeException(command.code());
        }
        if (uniquenessPort.productSlugExists(command.slug())) {
            throw new DuplicateProductSlugException(command.slug());
        }

        // The aggregate factory owns draft invariants; this service only orchestrates ports and policies.
        ProductAggregate draft = ProductAggregate.createDraft(
                null,
                command.code(),
                command.slug(),
                command.name(),
                command.shortDescription(),
                command.description());
        ProductAggregate saved = saveAdminProductPort.saveDraft(draft);
        CreateProductDraftResult result = new CreateProductDraftResult(
                saved.id(),
                saved.status(),
                saved.version(),
                false);
        idempotencyPort.storeCreateDraftOutcome(
                actorId,
                command.idempotencyKey(),
                command.commandName(),
                requestHash,
                saved.id(),
                result);
        // Audit accepted mutations with trace id and version so admin actions can be explained later.
        auditPort.record(
                actorId,
                command.traceId().value(),
                command.commandName(),
                saved.id(),
                "SUCCESS",
                null,
                saved.version());
        return result;
    }

    // Mark the replayed result so the web/API layer can later expose replay behavior if the contract needs it.
    private CreateProductDraftResult replayed(CreateProductDraftResult original) {
        return new CreateProductDraftResult(
                original.id(),
                original.status(),
                original.version(),
                true);
    }
}
