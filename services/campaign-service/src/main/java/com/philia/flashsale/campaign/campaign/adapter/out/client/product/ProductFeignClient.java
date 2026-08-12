package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Transport-only declaration of Product's internal Campaign validation contract. */
@FeignClient(
        name = "campaign-product",
        contextId = "campaignProductValidationClient",
        url = "${flashsale.campaign.downstream.product.base-url}",
        configuration = ProductFeignConfiguration.class)
public interface ProductFeignClient {

    @PostMapping(
            path = "/internal/v1/catalog/variants/campaign-validation",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ProductValidationResponse validate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Trace-Id") String traceId,
            @RequestBody ProductValidationRequest request);
}
