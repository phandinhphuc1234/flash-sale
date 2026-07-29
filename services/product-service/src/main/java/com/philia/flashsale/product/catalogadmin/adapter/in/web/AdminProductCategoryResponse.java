package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.util.UUID;

record AdminProductCategoryResponse(
        UUID id,
        boolean primary,
        int sortOrder) {
}
