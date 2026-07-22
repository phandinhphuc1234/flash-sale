package com.philia.flashsale.product.adapter.in.web.admin;

import java.net.URI;
import java.util.UUID;

import com.philia.flashsale.product.application.port.in.BrowseAdminCatalogUseCase;
import com.philia.flashsale.product.application.port.in.CreateProductDraftUseCase;
import com.philia.flashsale.product.application.port.in.ViewAdminProductUseCase;
import com.philia.flashsale.product.application.query.BrowseAdminCatalogQuery;
import com.philia.flashsale.product.application.query.ViewAdminProductQuery;
import com.philia.flashsale.product.application.result.AdminCatalogPageResult;
import com.philia.flashsale.product.application.result.AdminProductDetailResult;
import com.philia.flashsale.product.application.result.AdminProductSummaryResult;
import com.philia.flashsale.product.application.result.CreateProductDraftResult;
import com.philia.flashsale.product.domain.model.CatalogAdminActor;
import com.philia.flashsale.product.domain.model.ProductStatus;
import com.philia.flashsale.product.domain.model.TraceId;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/catalog")
class ProductAdminController {

    private final CreateProductDraftUseCase createProductDraftUseCase;
    private final BrowseAdminCatalogUseCase browseAdminCatalogUseCase;
    private final ViewAdminProductUseCase viewAdminProductUseCase;
    private final ProductAdminWebMapper mapper;

    ProductAdminController(
            CreateProductDraftUseCase createProductDraftUseCase,
            BrowseAdminCatalogUseCase browseAdminCatalogUseCase,
            ViewAdminProductUseCase viewAdminProductUseCase,
            ProductAdminWebMapper mapper) {
        this.createProductDraftUseCase = createProductDraftUseCase;
        this.browseAdminCatalogUseCase = browseAdminCatalogUseCase;
        this.viewAdminProductUseCase = viewAdminProductUseCase;
        this.mapper = mapper;
    }

    // Convert the admin HTTP request into an application command; business rules stay inside the use case/domain.
    @PostMapping("/products")
    ResponseEntity<CreateProductDraftResponse> createProductDraft(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Trace-Id") String traceId,
            @Valid @RequestBody CreateProductDraftRequest request) {
        CreateProductDraftResult result = createProductDraftUseCase.createDraft(
                mapper.toCommand(
                        request,
                        currentActor(),
                        requiredTraceId(traceId),
                        requiredHeader(idempotencyKey, "Idempotency-Key")));
        return ResponseEntity.created(URI.create("/api/v1/admin/catalog/products/" + result.id()))
                .body(mapper.toResponse(result));
    }

    // Admin browsing can include draft/inactive products, so it is intentionally separate from the public catalog API.
    @GetMapping("/products")
    AdminCatalogPageResponse<AdminProductSummaryResponse> browseProducts(
            @RequestHeader("X-Trace-Id") String traceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requiredTraceId(traceId);
        ProductStatus parsedStatus = parseStatus(status);
        AdminCatalogPageResult<AdminProductSummaryResult> result =
                browseAdminCatalogUseCase.browse(
                        new BrowseAdminCatalogQuery(parsedStatus, q, page, size));
        return mapper.toSummaryPage(result);
    }

    // Admin detail uses the product id because code/slug may still be edited while the product is a draft.
    @GetMapping("/products/{productId}")
    AdminProductDetailResponse viewProduct(
            @PathVariable UUID productId,
            @RequestHeader("X-Trace-Id") String traceId) {
        requiredTraceId(traceId);
        return mapper.toResponse(viewAdminProductUseCase.view(new ViewAdminProductQuery(productId)));
    }

    // Product-service still derives the actor from the validated JWT instead of trusting gateway-only metadata.
    private CatalogAdminActor currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated catalog admin actor is required");
        }
        return new CatalogAdminActor(requiredBoundedText(
                authentication.getName(), "Authenticated catalog admin actor", 128));
    }

    private ProductStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return ProductStatus.valueOf(status.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Status must be one of DRAFT, ACTIVE, INACTIVE, ARCHIVED");
        }
    }

    private TraceId requiredTraceId(String traceId) {
        return new TraceId(requiredBoundedText(traceId, "X-Trace-Id", 128));
    }

    private String requiredHeader(String value, String headerName) {
        return requiredBoundedText(value, headerName, 128);
    }

    private String requiredBoundedText(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
