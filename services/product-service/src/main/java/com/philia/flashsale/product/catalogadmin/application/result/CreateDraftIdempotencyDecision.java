package com.philia.flashsale.product.catalogadmin.application.result;

import java.util.Objects;

/**
 * Describes whether a create-draft command may start, must replay, or conflicts with an active key.
 */
public record CreateDraftIdempotencyDecision(
        Type type,
        CreateProductDraftResult replayResult) {

    public CreateDraftIdempotencyDecision {
        Objects.requireNonNull(type, "Idempotency decision type is required");
        if ((type == Type.REPLAY) != (replayResult != null)) {
            throw new IllegalArgumentException("Only a replay decision may contain a replay result");
        }
    }

    public static CreateDraftIdempotencyDecision fresh() {
        return new CreateDraftIdempotencyDecision(Type.FRESH, null);
    }

    public static CreateDraftIdempotencyDecision replay(CreateProductDraftResult result) {
        return new CreateDraftIdempotencyDecision(Type.REPLAY, Objects.requireNonNull(result));
    }

    public static CreateDraftIdempotencyDecision conflict() {
        return new CreateDraftIdempotencyDecision(Type.CONFLICT, null);
    }

    public enum Type {
        FRESH,
        REPLAY,
        CONFLICT
    }
}
