package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.util.UUID;

record AdminProductMediaResponse(
        UUID id,
        UUID variantId,
        String mediaType,
        String url,
        String altText,
        int sortOrder,
        String status) {
}
