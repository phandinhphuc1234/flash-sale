package com.philia.flashsale.product.application.port.in;

import java.util.List;
import java.util.UUID;

import com.philia.flashsale.product.domain.model.CatalogPage;
import com.philia.flashsale.product.domain.model.CategorySummary;
import com.philia.flashsale.product.domain.model.ProductDetail;
import com.philia.flashsale.product.domain.model.ProductSummary;

public interface BrowseCatalogUseCase {

    List<CategorySummary> browseCategories(UUID parentId);

    CatalogPage<ProductSummary> browseProducts(String categorySlug, int page, int size);

    ProductDetail viewProduct(String slug);
}
