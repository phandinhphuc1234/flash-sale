package com.philia.flashsale.product.adapter.in.web.admin;

import java.math.BigDecimal;

import com.philia.flashsale.product.application.command.CreateProductDraftCommand;
import com.philia.flashsale.product.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.application.result.AdminPageMetadata;
import com.philia.flashsale.product.application.result.AdminProductCategoryResult;
import com.philia.flashsale.product.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.application.result.AdminProductMediaResult;
import com.philia.flashsale.product.application.result.AdminProductSummaryResult;
import com.philia.flashsale.product.application.result.AdminProductVariantResult;
import com.philia.flashsale.product.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.domain.model.CatalogAdminActor;
import com.philia.flashsale.product.domain.model.TraceId;
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

    @Mapping(target = "status", expression = "java(product.status().name())")
    AdminProductSummaryResponse toResponse(AdminProductSummaryResult product);

    @Mapping(target = "status", expression = "java(product.status().name())")
    AdminProductDetailResponse toResponse(AdminProductDetailResult product);

    @Mapping(target = "basePrice", expression = "java(toPlainString(variant.basePrice()))")
    @Mapping(target = "status", expression = "java(variant.status().name())")
    AdminProductVariantResponse toResponse(AdminProductVariantResult variant);

    AdminProductCategoryResponse toResponse(AdminProductCategoryResult category);

    AdminProductMediaResponse toResponse(AdminProductMediaResult media);

    AdminPageResponse toResponse(AdminPageMetadata page);

    // Admin list wrapper is an HTTP response shape; keep this composition in the inbound adapter.
    default AdminCatalogPageResponse<AdminProductSummaryResponse> toSummaryPage(
            AdminCatalogPageResult<AdminProductSummaryResult> result) {
        return new AdminCatalogPageResponse<>(result.data().stream()
                .map(this::toResponse)
                .toList(), toResponse(result.page()));
    }

    default String toPlainString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
