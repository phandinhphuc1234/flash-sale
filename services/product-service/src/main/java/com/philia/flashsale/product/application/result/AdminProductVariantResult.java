package com.philia.flashsale.product.application.result;

import java.math.BigDecimal;
import java.util.UUID;

import com.philia.flashsale.product.domain.model.VariantStatus;

public record AdminProductVariantResult(
        UUID id,
        String sku,
        String barcode,
        String name,
        BigDecimal basePrice,
        String currency,
        VariantStatus status,
        int sortOrder) {
}
