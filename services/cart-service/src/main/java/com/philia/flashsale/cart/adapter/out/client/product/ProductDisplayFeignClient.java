package com.philia.flashsale.cart.adapter.out.client.product;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Transport-only declaration of Product's internal Cart display contract. */
@FeignClient(
        name = "cart-product",
        contextId = "cartProductDisplayFeignClient",
        url = "${cart.product.base-url}",
        configuration = ProductDisplayFeignConfiguration.class)
public interface ProductDisplayFeignClient {

    @PostMapping(
            path = "/internal/v1/catalog/variants/display-details",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ProductDisplayWireModels.Envelope displayDetails(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Trace-Id") String traceId,
            @RequestBody ProductDisplayWireModels.Request request);
}
