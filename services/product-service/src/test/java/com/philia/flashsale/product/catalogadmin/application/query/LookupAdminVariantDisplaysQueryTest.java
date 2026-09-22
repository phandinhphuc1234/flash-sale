package com.philia.flashsale.product.catalogadmin.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LookupAdminVariantDisplaysQueryTest {

    @Test
    void removesDuplicateIdsWhilePreservingFirstSeenOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        var query = new LookupAdminVariantDisplaysQuery(List.of(first, second, first));

        assertEquals(List.of(first, second), query.variantIds());
    }

    @Test
    void rejectsEmptyAndOverLimitBatches() {
        assertThrows(IllegalArgumentException.class,
                () -> new LookupAdminVariantDisplaysQuery(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new LookupAdminVariantDisplaysQuery(
                        java.util.stream.IntStream.range(0, LookupAdminVariantDisplaysQuery.MAX_VARIANTS + 1)
                                .mapToObj(value -> UUID.randomUUID())
                                .toList()));
    }

    @Test
    void rejectsNullBatchEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> new LookupAdminVariantDisplaysQuery(java.util.Arrays.asList(UUID.randomUUID(), null)));
    }
}
