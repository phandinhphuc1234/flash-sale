package com.philia.flashsale.product.catalog.application.service;

import com.philia.flashsale.product.catalog.application.port.in.LookupVariantDisplaysUseCase;
import com.philia.flashsale.product.catalog.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.catalog.application.result.VariantDisplayResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Normalizes batch input and delegates the Product-owned projection through an outbound port. */
public final class ProductVariantDisplayQueryService implements LookupVariantDisplaysUseCase {

    private final LoadCatalogPort loadCatalogPort;

    public ProductVariantDisplayQueryService(LoadCatalogPort loadCatalogPort) {
        this.loadCatalogPort = Objects.requireNonNull(loadCatalogPort);
    }

    @Override
    public List<VariantDisplayResult> lookup(List<UUID> variantIds) {
        if (variantIds == null) {
            throw new IllegalArgumentException("variantIds is required");
        }
        List<UUID> orderedUniqueIds = new ArrayList<>(new LinkedHashSet<>(variantIds));
        if (orderedUniqueIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("variantIds cannot contain null");
        }
        if (orderedUniqueIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(loadCatalogPort.loadVariantDisplays(orderedUniqueIds));
    }
}
