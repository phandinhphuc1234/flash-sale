package com.philia.flashsale.product.catalogquery.adapter.in.web;

import com.philia.flashsale.common.web.ApiResponse;
import com.philia.flashsale.product.catalogquery.application.port.in.LookupPurchaseQuotesUseCase;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Order-only Product decision endpoint; it reads current catalog state and never reserves it. */
@RestController
@RequestMapping(path = "/internal/v1/catalog/variants", produces = MediaType.APPLICATION_JSON_VALUE)
public class PurchaseQuoteController {

    private final LookupPurchaseQuotesUseCase lookupPurchaseQuotes;

    public PurchaseQuoteController(LookupPurchaseQuotesUseCase lookupPurchaseQuotes) {
        this.lookupPurchaseQuotes = Objects.requireNonNull(lookupPurchaseQuotes);
    }

    @PostMapping(path = "/purchase-quotes", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<PurchaseQuoteBatchResponse> purchaseQuotes(
            @Valid @RequestBody PurchaseQuoteBatchRequest request) {
        return ApiResponse.success(
                "Purchase quotes retrieved",
                PurchaseQuoteBatchResponse.from(lookupPurchaseQuotes.lookup(request.variantIds())));
    }
}
