package com.philia.flashsale.product.adapter.in.web;

import java.util.List;

record CatalogListResponse<T>(
        List<T> data) {
}
