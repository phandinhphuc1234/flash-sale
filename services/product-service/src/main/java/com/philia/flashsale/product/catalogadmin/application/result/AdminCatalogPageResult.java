package com.philia.flashsale.product.catalogadmin.application.result;

import java.util.List;

public record AdminCatalogPageResult<T>(
        List<T> data,
        AdminPageMetadata page) {

    public AdminCatalogPageResult {
        data = List.copyOf(data);
    }
}
