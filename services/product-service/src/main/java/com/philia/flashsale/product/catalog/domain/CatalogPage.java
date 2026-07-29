package com.philia.flashsale.product.catalog.domain;

import java.util.List;

public record CatalogPage<T>(
        List<T> data,
        PageMetadata page) {
}
