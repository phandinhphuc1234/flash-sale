package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.order.application.port.out.CurrentTimePort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderIdentityPort;
import com.philia.flashsale.order.order.application.port.out.GenerateOrderNumberPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.CreateRegularStockHoldPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadProductPurchaseQuotesPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.LoadCartCheckoutSnapshotPort;
import com.philia.flashsale.order.regularpurchase.application.port.out.PersistRegularPurchasePort;
import com.philia.flashsale.order.regularpurchase.application.usecase.RegularPurchaseCheckoutService;
import com.philia.flashsale.order.observability.OrderObservability;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition root for the runtime-gated regular Buy Now orchestration. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression("'${order.regular-purchase.runtime.intake-enabled:false}' == 'true' || '${order.regular-purchase.runtime.recovery-enabled:false}' == 'true'")
@EnableScheduling
public class RegularPurchaseCheckoutConfiguration {

    @Bean
    RegularPurchaseCheckoutService regularPurchaseCheckoutService(PersistRegularPurchasePort persistence,
            LoadProductPurchaseQuotesPort productQuotes, CreateRegularStockHoldPort stockHolds,
            LoadCartCheckoutSnapshotPort cartSnapshots,
            @Qualifier("generateOrderIdentityPort") GenerateOrderIdentityPort identities,
            @Qualifier("generateOrderNumberPort") GenerateOrderNumberPort orderNumbers,
            @Qualifier("currentTimePort") CurrentTimePort clock,
            OrderObservability observability) {
        return new RegularPurchaseCheckoutService(persistence, productQuotes, stockHolds, cartSnapshots,
                identities, orderNumbers, clock, observability);
    }

}
