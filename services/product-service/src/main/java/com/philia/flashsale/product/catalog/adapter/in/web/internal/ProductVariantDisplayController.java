package com.philia.flashsale.product.catalog.adapter.in.web.internal;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.product.catalog.application.port.in.LookupVariantDisplaysUseCase;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Product-owned internal endpoint for Cart's single batch display lookup. */
@RestController
@RequestMapping(path = "/internal/v1/catalog/variants", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductVariantDisplayController {

    private final LookupVariantDisplaysUseCase lookupVariantDisplays;

    public ProductVariantDisplayController(LookupVariantDisplaysUseCase lookupVariantDisplays) {
        this.lookupVariantDisplays = Objects.requireNonNull(lookupVariantDisplays);
    }

    @PostMapping(path = "/display-details", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<VariantDisplayBatchResponse> displayDetails(
            @Valid @RequestBody VariantDisplayBatchRequest request) {
        return ApiResponse.success(VariantDisplayBatchResponse.from(
                lookupVariantDisplays.lookup(request.variantIds())));
    }
}
