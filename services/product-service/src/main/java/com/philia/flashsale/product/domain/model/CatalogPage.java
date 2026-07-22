package com.philia.flashsale.product.domain.model;

import java.util.List;

public record CatalogPage<T>(
        List<T> data,
        PageMetadata page) {
}
