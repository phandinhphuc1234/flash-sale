package com.philia.flashsale.product.adapter.in.web.admin;

import java.util.UUID;

record CreateProductDraftResponse(
        UUID id,
        String status,
        long version) {
}
