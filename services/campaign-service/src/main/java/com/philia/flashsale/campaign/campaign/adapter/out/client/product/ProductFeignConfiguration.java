package com.philia.flashsale.campaign.campaign.adapter.out.client.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** Product-client-only policies; this class is not globally component-scanned. */
class ProductFeignConfiguration {

    @Bean
    ErrorDecoder productFeignErrorDecoder(ObjectMapper objectMapper) {
        return new ProductFeignErrorDecoder(objectMapper);
    }

    @Bean
    Retryer productFeignRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
