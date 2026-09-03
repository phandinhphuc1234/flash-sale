package com.philia.flashsale.cart.configuration;

import com.philia.flashsale.cart.application.port.in.GetCartUseCase;
import com.philia.flashsale.cart.application.port.out.LoadCartPort;
import com.philia.flashsale.cart.application.port.out.LoadProductDisplaysPort;
import com.philia.flashsale.cart.application.usecase.GetCartService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

/** Composition root for the Cart read use case and its enrichment capability. */
@Configuration(proxyBeanMethods = false)
public class CartQueryConfiguration {
    @Bean
    @ConditionalOnBean({LoadCartPort.class, LoadProductDisplaysPort.class})
    GetCartService getCartService(LoadCartPort carts, LoadProductDisplaysPort products) {
        return new GetCartService(carts, products);
    }

    @Bean
    @ConditionalOnBean(GetCartService.class)
    GetCartUseCase getCartUseCase(@Qualifier("getCartService") GetCartService service) {
        return service;
    }
}
