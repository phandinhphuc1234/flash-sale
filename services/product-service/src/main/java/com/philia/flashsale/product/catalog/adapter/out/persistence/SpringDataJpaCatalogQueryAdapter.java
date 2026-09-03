package com.philia.flashsale.product.catalog.adapter.out.persistence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.philia.flashsale.product.catalog.application.port.out.LoadCatalogPort;
import com.philia.flashsale.product.catalog.application.result.VariantDisplayResult;
import com.philia.flashsale.product.catalog.application.service.CategoryNotFoundException;
import com.philia.flashsale.product.catalog.application.service.ProductNotFoundException;
import com.philia.flashsale.product.catalog.domain.CatalogPage;
import com.philia.flashsale.product.catalog.domain.CatalogPageRequest;
import com.philia.flashsale.product.catalog.domain.CategorySummary;
import com.philia.flashsale.product.catalog.domain.PageMetadata;
import com.philia.flashsale.product.catalog.domain.ProductCategorySummary;
import com.philia.flashsale.product.catalog.domain.ProductDetail;
import com.philia.flashsale.product.catalog.domain.ProductMediaSummary;
import com.philia.flashsale.product.catalog.domain.ProductSummary;
import com.philia.flashsale.product.catalog.domain.ProductVariantSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
class SpringDataJpaCatalogQueryAdapter implements LoadCatalogPort {

    private static final String ACTIVE = "ACTIVE";

    private final ProductReadJpaRepository productRepository;
    private final CategoryReadJpaRepository categoryRepository;

    SpringDataJpaCatalogQueryAdapter(
            ProductReadJpaRepository productRepository,
            CategoryReadJpaRepository categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    // Read categories from JPA and return domain-facing summaries instead of exposing entities outward.
    public List<CategorySummary> loadCategories(UUID parentId) {
        List<CategoryJpaEntity> categories = parentId == null
                ? categoryRepository.findByParentIdIsNullAndStatusOrderBySortOrderAscIdAsc(ACTIVE)
                : categoryRepository.findByParentIdAndStatusOrderBySortOrderAscIdAsc(parentId, ACTIVE);
        return categories.stream()
                .map(category -> new CategorySummary(
                        category.getId(),
                        category.getParentId(),
                        category.getSlug(),
                        category.getName(),
                        category.getSortOrder()))
                .toList();
    }

    @Override
    // Fetch product rows first, then batch-load variants to avoid an N+1 query pattern on catalog pages.
    public CatalogPage<ProductSummary> loadVisibleProducts(
            String categorySlug,
            CatalogPageRequest pageRequest) {
        if (categorySlug != null && !categoryRepository.existsBySlug(categorySlug)) {
            throw new CategoryNotFoundException(categorySlug);
        }

        PageRequest pageable = PageRequest.of(pageRequest.page(), pageRequest.size());
        Page<ProductReadJpaRepository.ProductRow> page = categorySlug == null
                ? productRepository.findVisibleProducts(pageable)
                : productRepository.findVisibleProductsByCategorySlug(categorySlug, pageable);

        List<UUID> productIds = page.getContent().stream()
                .map(ProductReadJpaRepository.ProductRow::getId)
                .toList();
        Map<UUID, List<ProductVariantSummary>> variants = variantsByProductId(productIds);

        List<ProductSummary> products = page.getContent().stream()
                .map(product -> new ProductSummary(
                        product.getId(),
                        product.getCode(),
                        product.getSlug(),
                        product.getName(),
                        product.getShortDescription(),
                        variants.getOrDefault(product.getId(), List.of())))
                .toList();

        return new CatalogPage<>(
                products,
                new PageMetadata(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages(),
                        page.hasNext()));
    }

    @Override
    // Detail view composes one public read model from product, variants, categories, and media tables.
    public ProductDetail loadVisibleProduct(String slug) {
        ProductReadJpaRepository.ProductRow product = productRepository.findVisibleProductBySlug(slug)
                .orElseThrow(() -> new ProductNotFoundException(slug));
        List<UUID> productIds = List.of(product.getId());

        return new ProductDetail(
                product.getId(),
                product.getCode(),
                product.getSlug(),
                product.getName(),
                product.getShortDescription(),
                product.getDescription(),
                variantsByProductId(productIds).getOrDefault(product.getId(), List.of()),
                categoriesByProductId(productIds).getOrDefault(product.getId(), List.of()),
                mediaByProductId(productIds).getOrDefault(product.getId(), List.of()));
    }

    @Override
    // Batch loading keeps Cart reads to one Product query and returns missing ids as explicit data outcomes.
    public List<VariantDisplayResult> loadVariantDisplays(List<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, VariantDisplayResult> found = productRepository
                .findVariantDisplaysByIds(variantIds)
                .stream()
                .collect(Collectors.toMap(
                        ProductReadJpaRepository.VariantDisplayRow::getVariantId,
                        row -> new VariantDisplayResult(
                                row.getVariantId(),
                                true,
                                row.isSellable(),
                                row.getProductId(),
                                row.getProductSlug(),
                                row.getProductName(),
                                row.getVariantName(),
                                row.getSku(),
                                row.getBasePrice(),
                                row.getCurrency(),
                                row.getPrimaryImageUrl()),
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        return variantIds.stream()
                .map(id -> found.getOrDefault(id, VariantDisplayResult.missing(id)))
                .toList();
    }

    // Batch helpers preserve ordering from repository queries while grouping rows by their owning product id.
    private Map<UUID, List<ProductVariantSummary>> variantsByProductId(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findActiveVariantsByProductIds(productIds).stream()
                .collect(groupingByProductId(
                        ProductReadJpaRepository.VariantRow::getProductId,
                        row -> new ProductVariantSummary(
                                row.getId(),
                                row.getSku(),
                                row.getName(),
                                row.getBasePrice(),
                                row.getCurrency())));
    }

    private Map<UUID, List<ProductCategorySummary>> categoriesByProductId(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findCategoriesByProductIds(productIds).stream()
                .collect(groupingByProductId(
                        ProductReadJpaRepository.ProductCategoryRow::getProductId,
                        row -> new ProductCategorySummary(
                                row.getId(),
                                row.getSlug(),
                                row.getName(),
                                row.isPrimaryCategory(),
                                row.getSortOrder())));
    }

    private Map<UUID, List<ProductMediaSummary>> mediaByProductId(Collection<UUID> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findActiveMediaByProductIds(productIds).stream()
                .collect(groupingByProductId(
                        ProductReadJpaRepository.ProductMediaRow::getProductId,
                        row -> new ProductMediaSummary(
                                row.getId(),
                                row.getMediaType(),
                                row.getUrl(),
                                row.getAltText(),
                                row.getSortOrder())));
    }

    private static <T, R> Collector<T, ?, Map<UUID, List<R>>> groupingByProductId(
            Function<T, UUID> productId,
            Function<T, R> mapper) {
        return Collectors.groupingBy(
                productId,
                LinkedHashMap::new,
                Collectors.mapping(mapper, Collectors.toCollection(ArrayList::new)));
    }
}
