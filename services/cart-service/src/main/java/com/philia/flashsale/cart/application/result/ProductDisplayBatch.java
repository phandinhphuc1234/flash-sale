package com.philia.flashsale.cart.application.result;

import java.util.List;
import java.util.UUID;

/** Ordered Product results; detailsAvailable is false only when Product could not be trusted. */
public record ProductDisplayBatch(List<ProductDisplay> displays, boolean detailsAvailable) {

    public ProductDisplayBatch {
        displays = displays == null ? List.of() : List.copyOf(displays);
    }

    public static ProductDisplayBatch available(List<ProductDisplay> displays) {
        return new ProductDisplayBatch(displays, true);
    }

    public static ProductDisplayBatch unavailable(List<UUID> variantIds) {
        return new ProductDisplayBatch(
                variantIds.stream().map(ProductDisplay::unavailable).toList(), false);
    }
}
