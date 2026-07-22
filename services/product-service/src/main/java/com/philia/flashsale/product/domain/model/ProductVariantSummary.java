package com.philia.flashsale.product.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductVariantSummary(
        UUID id,
        String sku,
        String name,
        BigDecimal basePrice,
        String currency) {
}
