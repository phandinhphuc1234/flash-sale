package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import com.philia.flashsale.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/** Transport-only declaration of Inventory's idempotent Order-only regular hold contract. */
@FeignClient(
        name = "order-inventory",
        contextId = "orderInventoryRegularHoldClient",
        url = "${order.regular-purchase.internal-clients.inventory-base-url}",
        configuration = InventoryRegularHoldFeignConfiguration.class)
public interface InventoryRegularHoldFeignClient {

    @PostMapping(
            path = "/internal/v1/regular-stock-holds",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    ApiResponse<InventoryRegularHoldResponse> create(
            @RequestHeader("X-Trace-Id") String traceId,
            @RequestBody InventoryRegularHoldRequest request);
}
