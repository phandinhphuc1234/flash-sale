package com.philia.flashsale.cart.configuration;

import com.philia.flashsale.cart.application.port.in.ClearCartUseCase;
import com.philia.flashsale.cart.application.port.in.RemoveCartItemUseCase;
import com.philia.flashsale.cart.application.port.in.SetCartItemUseCase;
import com.philia.flashsale.cart.application.port.out.LoadProductDisplaysPort;
import com.philia.flashsale.cart.application.port.out.MaintainCartPort;
import com.philia.flashsale.cart.application.usecase.MaintainCartService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

/** Composition root for Cart item mutation use cases. */
@Configuration(proxyBeanMethods = false)
public class CartMutationConfiguration {
    @Bean
    @ConditionalOnBean({LoadProductDisplaysPort.class, MaintainCartPort.class})
    MaintainCartService maintainCartService(LoadProductDisplaysPort products, MaintainCartPort cart) {
        return new MaintainCartService(products, cart);
    }

    @Bean
    @ConditionalOnBean(MaintainCartService.class)
    SetCartItemUseCase setCartItemUseCase(@Qualifier("maintainCartService") MaintainCartService service) {
        return service;
    }

    @Bean
    @ConditionalOnBean(MaintainCartService.class)
    RemoveCartItemUseCase removeCartItemUseCase(@Qualifier("maintainCartService") MaintainCartService service) {
        return service;
    }

    @Bean
    @ConditionalOnBean(MaintainCartService.class)
    ClearCartUseCase clearCartUseCase(@Qualifier("maintainCartService") MaintainCartService service) {
        return service;
    }
}
