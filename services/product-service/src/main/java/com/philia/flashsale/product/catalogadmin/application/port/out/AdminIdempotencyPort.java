package com.philia.flashsale.product.catalogadmin.application.port.out;

import java.util.UUID;

import com.philia.flashsale.product.catalogadmin.application.result.CreateDraftIdempotencyDecision;
import com.philia.flashsale.product.catalogadmin.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.catalogadmin.application.result.MutationIdempotencyDecision;
import com.philia.flashsale.product.catalogadmin.domain.AdminCommandName;

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

    /**
     * Lifecycle/composition mutations use this extension of the original draft contract.
     * The default keeps small draft-only test doubles source-compatible; production adapters override it.
     */
    default MutationIdempotencyDecision resolveMutation(String actorId, String idempotencyKey, String requestHash) {
        return MutationIdempotencyDecision.fresh();
    }

    default void storeMutationOutcome(String actorId, String idempotencyKey, AdminCommandName commandName,
                                      String requestHash, UUID productId, int httpStatus, String status, long version) {
        // Draft-only adapters have no lifecycle outcome to persist.
    }
}
