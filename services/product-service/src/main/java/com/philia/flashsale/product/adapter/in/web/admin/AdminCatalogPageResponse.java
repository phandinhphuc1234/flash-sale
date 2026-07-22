package com.philia.flashsale.product.adapter.in.web.admin;

import java.util.List;

record AdminCatalogPageResponse<T>(
        List<T> data,
        AdminPageResponse page) {
}
