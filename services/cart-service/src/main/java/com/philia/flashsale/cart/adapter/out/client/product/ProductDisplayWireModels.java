package com.philia.flashsale.cart.adapter.out.client.product;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Feign-only JSON models; they never cross into Cart application or domain packages. */
public final class ProductDisplayWireModels {

    private ProductDisplayWireModels() { }

    public record Request(List<UUID> variantIds) { }

    public record Envelope(boolean success, String code, String message, Data data, Instant timestamp) { }

    public record Data(List<Variant> variants) { }

    public record Variant(
            UUID variantId,
            boolean found,
            boolean sellable,
            UUID productId,
            String productSlug,
            String productName,
            String variantName,
            String sku,
            BigDecimal basePrice,
            String currency,
            String primaryImageUrl) { }
}
