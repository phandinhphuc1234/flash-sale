package com.philia.flashsale.product.catalog.adapter.in.web;

import java.util.UUID;

record ProductCategoryResponse(
        UUID id,
        String slug,
        String name,
        boolean primary,
        int sortOrder) {
}
