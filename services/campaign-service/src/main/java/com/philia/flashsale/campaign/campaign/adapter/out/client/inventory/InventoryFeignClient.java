package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

import com.philia.flashsale.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Transport-only declaration of Inventory's idempotent Campaign allocation contract. */
@FeignClient(
        name = "campaign-inventory-allocation",
        contextId = "campaignInventoryAllocationClient",
        url = "${flashsale.campaign.downstream.inventory.base-url}",
        configuration = InventoryFeignConfiguration.class)
public interface InventoryFeignClient {

    @PostMapping(
            path = "/internal/v1/campaign-stock-allocations",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<InventoryAllocationResponse> allocate(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestHeader("X-Trace-Id") String traceId,
            @RequestBody InventoryAllocationRequest request);
}
