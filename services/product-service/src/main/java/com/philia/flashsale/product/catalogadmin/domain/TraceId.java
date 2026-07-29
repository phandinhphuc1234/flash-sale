package com.philia.flashsale.product.catalogadmin.domain;

import java.util.UUID;

public record TraceId(String value) {

    public TraceId {
        if (value == null || value.isBlank()) {
            value = UUID.randomUUID().toString();
        }
        value = value.trim();
    }
}
