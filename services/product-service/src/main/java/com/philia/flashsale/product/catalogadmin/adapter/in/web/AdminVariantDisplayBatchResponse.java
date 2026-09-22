package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.util.List;

record AdminVariantDisplayBatchResponse(List<AdminVariantDisplayResponse> variants) {
    AdminVariantDisplayBatchResponse {
        variants = List.copyOf(variants);
    }
}
