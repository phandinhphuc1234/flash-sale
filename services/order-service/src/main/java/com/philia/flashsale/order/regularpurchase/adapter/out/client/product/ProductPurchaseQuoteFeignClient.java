package com.philia.flashsale.order.regularpurchase.adapter.out.client.product;

import com.philia.flashsale.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Transport-only declaration of Product's Order-only purchase-quote contract. */
@FeignClient(
        name = "order-product",
        contextId = "orderProductPurchaseQuoteClient",
        url = "${order.regular-purchase.internal-clients.product-base-url}",
        configuration = ProductPurchaseQuoteFeignConfiguration.class)
public interface ProductPurchaseQuoteFeignClient {

    @PostMapping(
            path = "/internal/v1/catalog/variants/purchase-quotes",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<ProductPurchaseQuoteBatchResponse> purchaseQuotes(
            @RequestHeader("X-Trace-Id") String traceId,
            @RequestBody ProductPurchaseQuoteBatchRequest request);
}
