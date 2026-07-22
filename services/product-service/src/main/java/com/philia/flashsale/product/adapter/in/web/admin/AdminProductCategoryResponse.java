package com.philia.flashsale.product.adapter.in.web.admin;

import java.util.UUID;

record AdminProductCategoryResponse(
        UUID id,
        boolean primary,
        int sortOrder) {
}
