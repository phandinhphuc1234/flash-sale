package com.philia.flashsale.order.regularpurchase.adapter.out.client.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/** Product-client-only policies: raw remote errors are decoded and retries remain orchestration-owned. */
class ProductPurchaseQuoteFeignConfiguration {

    @Bean
    ErrorDecoder productPurchaseQuoteErrorDecoder(ObjectMapper objectMapper) {
        return new ProductPurchaseQuoteFeignErrorDecoder(objectMapper);
    }

    @Bean
    Retryer productPurchaseQuoteRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
