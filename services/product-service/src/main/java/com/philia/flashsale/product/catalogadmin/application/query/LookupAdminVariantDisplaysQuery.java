package com.philia.flashsale.product.catalogadmin.application.query;

import java.util.List;
import java.util.UUID;

/** Bounded, de-duplicated request for admin display labels. */
public record LookupAdminVariantDisplaysQuery(List<UUID> variantIds) {
    public static final int MAX_VARIANTS = 100;

    public LookupAdminVariantDisplaysQuery {
        if (variantIds == null || variantIds.isEmpty() || variantIds.size() > MAX_VARIANTS) {
            throw new IllegalArgumentException("variantIds must contain between 1 and " + MAX_VARIANTS + " IDs");
        }
        if (variantIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("variantIds must not contain null values");
        }
        variantIds = List.copyOf(new java.util.LinkedHashSet<>(variantIds));
    }
}
