package com.philia.flashsale.product.catalogadmin.application.result;

import java.util.UUID;

public record AdminProductMediaResult(
        UUID id,
        UUID variantId,
        String mediaType,
        String url,
        String altText,
        int sortOrder,
        String status) {
}
