package com.philia.flashsale.campaign.campaign.adapter.out.client.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** Inventory-client-only policies; retries stay owned by the durable Campaign workflow. */
class InventoryFeignConfiguration {

    @Bean
    ErrorDecoder inventoryFeignErrorDecoder(ObjectMapper objectMapper) {
        return new InventoryFeignErrorDecoder(objectMapper);
    }

    @Bean
    Retryer inventoryFeignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
