package com.philia.flashsale.product.application.port.out;

import java.util.UUID;

import com.philia.flashsale.product.application.result.CreateDraftIdempotencyDecision;
import com.philia.flashsale.product.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.domain.model.AdminCommandName;

public interface AdminIdempotencyPort {

    /**
     * Resolves the key atomically for the current command transaction.
     *
     * <p>A fresh decision reserves serialized execution until that transaction completes.</p>
     */
    CreateDraftIdempotencyDecision resolveCreateDraft(
            String actorId,
            String idempotencyKey,
            String requestHash);

    void storeCreateDraftOutcome(
            String actorId,
            String idempotencyKey,
            AdminCommandName commandName,
            String requestHash,
            UUID productId,
            CreateProductDraftResult result);
}
