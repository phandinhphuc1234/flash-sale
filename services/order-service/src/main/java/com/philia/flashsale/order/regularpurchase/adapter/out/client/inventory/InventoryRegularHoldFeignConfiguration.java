package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** Inventory-client-only policy: no blind transport retry can create a second hold identity. */
class InventoryRegularHoldFeignConfiguration {

    @Bean
    ErrorDecoder inventoryRegularHoldErrorDecoder(ObjectMapper objectMapper) {
        return new InventoryRegularHoldFeignErrorDecoder(objectMapper);
    }

    @Bean
    Retryer inventoryRegularHoldRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
