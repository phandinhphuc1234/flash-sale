package com.philia.flashsale.product.catalog.adapter.in.web;

import java.util.UUID;

record CategoryResponse(
        UUID id,
        UUID parentId,
        String slug,
        String name,
        int sortOrder) {
}
