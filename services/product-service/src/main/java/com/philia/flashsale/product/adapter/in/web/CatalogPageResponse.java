package com.philia.flashsale.product.adapter.in.web;

import java.util.List;

record CatalogPageResponse<T>(
        List<T> data,
        PageResponse page) {
}
