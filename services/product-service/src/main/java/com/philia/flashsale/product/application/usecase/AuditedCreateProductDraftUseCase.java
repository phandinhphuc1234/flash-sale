package com.philia.flashsale.product.application.usecase;

import com.philia.flashsale.product.application.command.CreateProductDraftCommand;
import com.philia.flashsale.product.application.exception.DuplicateProductCodeException;
import com.philia.flashsale.product.application.exception.DuplicateProductSlugException;
import com.philia.flashsale.product.application.exception.IdempotencyKeyReusedException;
import com.philia.flashsale.product.application.port.in.CreateProductDraftUseCase;
import com.philia.flashsale.product.application.port.out.RecordCatalogAdminAuditPort;
import com.philia.flashsale.product.application.result.CreateProductDraftResult;

/**
 * Adds durable audit evidence for create-draft outcomes that happen outside the accepted mutation.
 */
public final class AuditedCreateProductDraftUseCase implements CreateProductDraftUseCase {

    private final CreateProductDraftUseCase delegate;
    private final RecordCatalogAdminAuditPort outcomeAuditPort;

    public AuditedCreateProductDraftUseCase(
            CreateProductDraftUseCase delegate,
            RecordCatalogAdminAuditPort outcomeAuditPort) {
        this.delegate = delegate;
        this.outcomeAuditPort = outcomeAuditPort;
    }

    @Override
    public CreateProductDraftResult createDraft(CreateProductDraftCommand command) {
        try {
            CreateProductDraftResult result = delegate.createDraft(command);
            if (result.replayed()) {
                record(command, result, "REPLAYED", null);
            }
            return result;
        } catch (IdempotencyKeyReusedException exception) {
            recordConflict(command, "IDEMPOTENCY_KEY_REUSED");
            throw exception;
        } catch (DuplicateProductCodeException exception) {
            recordConflict(command, "DUPLICATE_PRODUCT_CODE");
            throw exception;
        } catch (DuplicateProductSlugException exception) {
            recordConflict(command, "DUPLICATE_PRODUCT_SLUG");
            throw exception;
        }
    }

    private void recordConflict(CreateProductDraftCommand command, String errorCode) {
        outcomeAuditPort.record(
                command.actor().actorId(),
                command.traceId().value(),
                command.commandName(),
                null,
                "CONFLICT",
                errorCode,
                null);
    }

    private void record(
            CreateProductDraftCommand command,
            CreateProductDraftResult result,
            String outcome,
            String errorCode) {
        outcomeAuditPort.record(
                command.actor().actorId(),
                command.traceId().value(),
                command.commandName(),
                result.id(),
                outcome,
                errorCode,
                result.version());
    }
}
