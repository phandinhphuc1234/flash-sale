package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.regularpurchase.application.port.in.CheckoutBuyNowUseCase;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.application.usecase.RegularPurchaseCheckoutService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for the runtime-gated regular Buy Now orchestration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "order.regular-purchase.runtime.intake-enabled", havingValue = "true")
public class RegularPurchaseCheckoutConfiguration {

    @Bean
    CheckoutBuyNowUseCase checkoutBuyNowUseCase(PersistRegularPurchasePort persistence,
            LoadProductPurchaseQuotesPort productQuotes, CreateRegularStockHoldPort stockHolds,
            @Qualifier("generateOrderIdentityPort") GenerateOrderIdentityPort identities,
            @Qualifier("generateOrderNumberPort") GenerateOrderNumberPort orderNumbers,
            @Qualifier("currentTimePort") CurrentTimePort clock) {
        return new RegularPurchaseCheckoutService(persistence, productQuotes, stockHolds, identities, orderNumbers,
                clock);
    }
}
