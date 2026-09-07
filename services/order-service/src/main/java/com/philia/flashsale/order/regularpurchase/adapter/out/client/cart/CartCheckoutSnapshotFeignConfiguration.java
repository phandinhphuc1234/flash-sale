package com.philia.flashsale.order.regularpurchase.adapter.out.client.cart;

import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CartCheckoutSnapshotFeignConfiguration {
    @Bean
    ErrorDecoder cartCheckoutSnapshotErrorDecoder() {
        return (methodKey, response) -> new CartCheckoutSnapshotRemoteException(response.status());
    }
}
