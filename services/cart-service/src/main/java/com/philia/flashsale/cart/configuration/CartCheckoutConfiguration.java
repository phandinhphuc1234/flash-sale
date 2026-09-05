package com.philia.flashsale.cart.configuration;

import com.philia.flashsale.cart.application.port.in.GetCartCheckoutSnapshotUseCase;
import com.philia.flashsale.cart.application.port.out.LoadCartCheckoutSnapshotPort;
import com.philia.flashsale.cart.application.usecase.GetCartCheckoutSnapshotService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for the Order-only Cart checkout snapshot capability. */
@Configuration(proxyBeanMethods = false)
public class CartCheckoutConfiguration {
    @Bean
    @ConditionalOnBean(LoadCartCheckoutSnapshotPort.class)
    GetCartCheckoutSnapshotService getCartCheckoutSnapshotService(LoadCartCheckoutSnapshotPort carts) {
        return new GetCartCheckoutSnapshotService(carts);
    }

    @Bean
    @ConditionalOnBean(GetCartCheckoutSnapshotService.class)
    GetCartCheckoutSnapshotUseCase getCartCheckoutSnapshotUseCase(
            @Qualifier("getCartCheckoutSnapshotService") GetCartCheckoutSnapshotService service) {
        return service;
    }
}
