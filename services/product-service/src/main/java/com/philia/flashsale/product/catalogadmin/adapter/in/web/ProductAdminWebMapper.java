package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import com.philia.flashsale.common.web.PageMeta;
import com.philia.flashsale.common.web.PageResponse;
import com.philia.flashsale.product.catalogadmin.application.command.MaintainProductCompositionCommand;
import com.philia.flashsale.product.catalogadmin.application.command.CreateProductDraftCommand;
import com.philia.flashsale.product.catalogadmin.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminPageMetadata;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductCategoryResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductMediaResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductSummaryResult;
import com.philia.flashsale.product.catalogadmin.application.result.AdminProductVariantResult;
import com.philia.flashsale.product.catalogadmin.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.catalogadmin.application.result.MaintainProductCompositionResult;
import com.philia.flashsale.product.catalogadmin.application.result.ProductLifecycleResult;
import com.philia.flashsale.product.catalogadmin.domain.CatalogAdminActor;
import com.philia.flashsale.product.catalogadmin.domain.TraceId;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductAdminWebMapper {

    @Mapping(target = "actor", source = "actor")
    @Mapping(target = "traceId", source = "traceId")
    @Mapping(target = "idempotencyKey", source = "idempotencyKey")
    @Mapping(target = "code", source = "request.code")
    @Mapping(target = "slug", source = "request.slug")
    @Mapping(target = "name", source = "request.name")
    @Mapping(target = "shortDescription", source = "request.shortDescription")
    @Mapping(target = "description", source = "request.description")
    CreateProductDraftCommand toCommand(
            CreateProductDraftRequest request,
            CatalogAdminActor actor,
            TraceId traceId,
            String idempotencyKey);

    @Mapping(target = "status", expression = "java(result.status().name())")
    CreateProductDraftResponse toResponse(CreateProductDraftResult result);

    /** Map the web payload explicitly so the application layer never depends on HTTP DTOs. */
    default MaintainProductCompositionCommand toCompositionCommand(
            MaintainProductCompositionRequest request,
            java.util.UUID productId,
            long expectedVersion,
            CatalogAdminActor actor,
            TraceId traceId) {
        List<MaintainProductCompositionCommand.VariantInput> variants = request.variants().stream()
                .map(v -> new MaintainProductCompositionCommand.VariantInput(
                        v.id(), v.sku(), v.barcode(), v.name(), v.basePrice(), v.currency(), v.status(), v.sortOrder()))
                .toList();
        List<MaintainProductCompositionCommand.CategoryInput> categories = request.categories().stream()
                .map(c -> new MaintainProductCompositionCommand.CategoryInput(c.id(), c.primary(), c.sortOrder()))
                .toList();
        List<MaintainProductCompositionCommand.MediaInput> media = request.media().stream()
                .map(m -> new MaintainProductCompositionCommand.MediaInput(
                        m.id(), m.variantId(), m.mediaType(), m.url(), m.altText(), m.sortOrder(), m.status()))
                .toList();
        return new MaintainProductCompositionCommand(productId, expectedVersion, request.name(),
                request.shortDescription(), request.description(), variants, categories, media, actor, traceId);
    }

    default ProductMutationResponse toResponse(MaintainProductCompositionResult result) {
        return new ProductMutationResponse(result.id(), result.version());
    }

    default ProductLifecycleResponse toResponse(ProductLifecycleResult result) {
        return new ProductLifecycleResponse(result.id(), result.status().name(), result.publishedAt(),
                result.version());
    }

    @Mapping(target = "status", expression = "java(product.status().name())")
    AdminProductSummaryResponse toResponse(AdminProductSummaryResult product);

    @Mapping(target = "status", expression = "java(product.status().name())")
    AdminProductDetailResponse toResponse(AdminProductDetailResult product);

    @Mapping(target = "basePrice", expression = "java(toPlainString(variant.basePrice()))")
    @Mapping(target = "status", expression = "java(variant.status().name())")
    AdminProductVariantResponse toResponse(AdminProductVariantResult variant);

    AdminProductCategoryResponse toResponse(AdminProductCategoryResult category);

    AdminProductMediaResponse toResponse(AdminProductMediaResult media);

    PageMeta toResponse(AdminPageMetadata page);

    // Admin list wrapper is an HTTP response shape; keep this composition in the inbound adapter.
    default PageResponse<AdminProductSummaryResponse> toSummaryPage(
            AdminCatalogPageResult<AdminProductSummaryResult> result) {
        return new PageResponse<>(result.data().stream()
                .map(this::toResponse)
                .toList(), toResponse(result.page()));
    }

    default String toPlainString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
