package com.philia.flashsale.product.catalogadmin.application.result;

import com.philia.flashsale.product.catalogadmin.domain.ProductStatus;
import com.philia.flashsale.product.catalogadmin.domain.VariantStatus;
import java.math.BigDecimal;
import java.util.UUID;

/** Product-owned display projection; missing rows are represented with found=false. */
public record AdminVariantDisplayResult(
        UUID variantId,
        boolean found,
        UUID productId,
        String productName,
        String variantName,
        String sku,
        BigDecimal basePrice,
        String currency,
        ProductStatus productStatus,
        VariantStatus variantStatus) {

    public static AdminVariantDisplayResult missing(UUID variantId) {
        return new AdminVariantDisplayResult(
                variantId, false, null, null, null, null, null, null, null, null);
    }
}
