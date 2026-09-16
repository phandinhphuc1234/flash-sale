package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring composition for the process-local Order-to-Inventory protection policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OrderInventoryResilienceProperties.class)
class OrderInventoryResilienceConfiguration {

    static final String CIRCUIT_BREAKER_NAME = "orderInventoryRegularHold";
    static final String BULKHEAD_NAME = "orderInventoryRegularHold";

    @Bean("orderInventoryCircuitBreakerRegistry")
    CircuitBreakerRegistry orderInventoryCircuitBreakerRegistry(OrderInventoryResilienceProperties properties) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(properties.slidingWindowSize())
                .minimumNumberOfCalls(properties.minimumNumberOfCalls())
                .failureRateThreshold(properties.failureRateThreshold())
                .waitDurationInOpenState(properties.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(properties.permittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(properties.automaticTransition())
                .build();
        return CircuitBreakerRegistry.of(config);
    }

    @Bean("orderInventoryRegularHoldCircuitBreaker")
    CircuitBreaker orderInventoryRegularHoldCircuitBreaker(
            @Qualifier("orderInventoryCircuitBreakerRegistry") CircuitBreakerRegistry registry) {
        return registry.circuitBreaker(CIRCUIT_BREAKER_NAME);
    }

    @Bean("orderInventoryBulkheadRegistry")
    BulkheadRegistry orderInventoryBulkheadRegistry(OrderInventoryResilienceProperties properties) {
        BulkheadConfig config = BulkheadConfig.custom()
                .maxConcurrentCalls(properties.maxConcurrentCalls())
                .maxWaitDuration(Duration.ZERO)
                .build();
        return BulkheadRegistry.of(config);
    }

    @Bean("orderInventoryRegularHoldBulkhead")
    Bulkhead orderInventoryRegularHoldBulkhead(
            @Qualifier("orderInventoryBulkheadRegistry") BulkheadRegistry registry) {
        return registry.bulkhead(BULKHEAD_NAME);
    }
}
