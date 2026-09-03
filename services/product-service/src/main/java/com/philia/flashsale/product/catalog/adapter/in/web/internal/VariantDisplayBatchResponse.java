package com.philia.flashsale.product.catalog.adapter.in.web.internal;

import com.philia.flashsale.product.catalog.application.result.VariantDisplayResult;
import java.util.List;
import java.util.UUID;

/** Internal response projection owned by Product and consumed by Cart. */
public record VariantDisplayBatchResponse(List<VariantDisplayResponse> variants) {

    public static VariantDisplayBatchResponse from(List<VariantDisplayResult> results) {
        return new VariantDisplayBatchResponse(results.stream()
                .map(VariantDisplayResponse::from)
                .toList());
    }

    public record VariantDisplayResponse(
            UUID variantId,
            boolean found,
            boolean sellable,
            UUID productId,
            String productSlug,
            String productName,
            String variantName,
            String sku,
            String basePrice,
            String currency,
            String primaryImageUrl) {

        static VariantDisplayResponse from(VariantDisplayResult result) {
            return new VariantDisplayResponse(
                    result.variantId(),
                    result.found(),
                    result.sellable(),
                    result.productId(),
                    result.productSlug(),
                    result.productName(),
                    result.variantName(),
                    result.sku(),
                    result.basePrice() == null ? null : result.basePrice().toPlainString(),
                    result.currency(),
                    result.primaryImageUrl());
        }
    }
}
