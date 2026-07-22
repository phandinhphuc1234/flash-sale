package com.philia.flashsale.product.adapter.in.web;

import java.util.UUID;

record ProductMediaResponse(
        UUID id,
        String mediaType,
        String url,
        String altText,
        int sortOrder) {
}
