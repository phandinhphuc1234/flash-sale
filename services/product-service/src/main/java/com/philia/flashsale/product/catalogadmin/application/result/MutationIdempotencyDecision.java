package com.philia.flashsale.product.catalogadmin.application.result;

import java.util.UUID;

public record MutationIdempotencyDecision(Type type, UUID productId, long version, String status) {
    public static MutationIdempotencyDecision fresh() { return new MutationIdempotencyDecision(Type.FRESH, null, 0, null); }
    public static MutationIdempotencyDecision replay(UUID id, long version, String status) {
        return new MutationIdempotencyDecision(Type.REPLAY, id, version, status);
    }
    public static MutationIdempotencyDecision conflict() { return new MutationIdempotencyDecision(Type.CONFLICT, null, 0, null); }
    public enum Type { FRESH, REPLAY, CONFLICT }
}
