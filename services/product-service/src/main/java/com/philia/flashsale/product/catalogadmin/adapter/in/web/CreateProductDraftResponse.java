package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.util.UUID;

record CreateProductDraftResponse(
        UUID id,
        String status,
        long version) {
}
