package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.order.adapter.out.persistence.jpa.OwnedOrderQueryJpaAdapter;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OrderLineJpaRepository;
import com.philia.flashsale.order.order.adapter.out.persistence.jpa.repository.OwnedOrderQueryJpaRepository;
import com.philia.flashsale.order.order.application.usecase.OrderQueryService;
import com.philia.flashsale.order.observability.OrderObservability;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owner query slice only when the service has a JPA persistence context. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "order.query.enabled", havingValue = "true", matchIfMissing = true)
public class OrderQueryConfiguration {

    @Bean
    public OwnedOrderQueryJpaAdapter ownedOrderQueryJpaAdapter(
            OwnedOrderQueryJpaRepository orders, OrderLineJpaRepository lines) {
        return new OwnedOrderQueryJpaAdapter(orders, lines);
    }

    @Bean
    public OrderQueryService orderQueryService(
            OwnedOrderQueryJpaAdapter adapter, OrderRuntimeProperties runtimeProperties,
            OrderObservability observability) {
        return new OrderQueryService(adapter, adapter, runtimeProperties.queryPageSizeMax(), observability);
    }

}
