package com.philia.flashsale.order.regularpurchase.adapter.out.client.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OrderInventoryResilienceConfigurationTests {

    private static final String PREFIX = "order.regular-purchase.inventory-resilience.";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OrderInventoryResilienceConfiguration.class);

    @Test
    void bindsTheApprovedConservativeDefaultsAndBuildsManagedInstances() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            OrderInventoryResilienceProperties properties =
                    context.getBean(OrderInventoryResilienceProperties.class);
            assertThat(properties.slidingWindowSize()).isEqualTo(20);
            assertThat(properties.minimumNumberOfCalls()).isEqualTo(10);
            assertThat(properties.failureRateThreshold()).isEqualTo(50.0f);
            assertThat(properties.waitDurationInOpenState()).isEqualTo(Duration.ofSeconds(10));
            assertThat(properties.permittedCallsInHalfOpenState()).isEqualTo(2);
            assertThat(properties.automaticTransition()).isFalse();
            assertThat(properties.maxConcurrentCalls()).isEqualTo(16);

            CircuitBreaker circuitBreaker = context.getBean(CircuitBreaker.class);
            assertThat(circuitBreaker.getName())
                    .isEqualTo(OrderInventoryResilienceConfiguration.CIRCUIT_BREAKER_NAME);
            assertThat(circuitBreaker.getCircuitBreakerConfig().getSlidingWindowSize()).isEqualTo(20);
            assertThat(circuitBreaker.getCircuitBreakerConfig().getSlidingWindowType())
                    .isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(circuitBreaker.getCircuitBreakerConfig().getMinimumNumberOfCalls()).isEqualTo(10);
            assertThat(circuitBreaker.getCircuitBreakerConfig().getFailureRateThreshold()).isEqualTo(50.0f);
            assertThat(circuitBreaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1))
                    .isEqualTo(10_000L);
            assertThat(circuitBreaker.getCircuitBreakerConfig().getPermittedNumberOfCallsInHalfOpenState())
                    .isEqualTo(2);
            assertThat(circuitBreaker.getCircuitBreakerConfig().isAutomaticTransitionFromOpenToHalfOpenEnabled())
                    .isFalse();

            Bulkhead bulkhead = context.getBean(Bulkhead.class);
            assertThat(bulkhead.getName()).isEqualTo(OrderInventoryResilienceConfiguration.BULKHEAD_NAME);
            assertThat(bulkhead.getBulkheadConfig().getMaxConcurrentCalls()).isEqualTo(16);
            assertThat(bulkhead.getBulkheadConfig().getMaxWaitDuration()).isZero();
        });
    }

    @Test
    void bindsExplicitExternalOverrides() {
        contextRunner.withPropertyValues(
                        PREFIX + "sliding-window-size=12",
                        PREFIX + "minimum-number-of-calls=6",
                        PREFIX + "failure-rate-threshold=40",
                        PREFIX + "wait-duration-in-open-state=7s",
                        PREFIX + "permitted-calls-in-half-open-state=3",
                        PREFIX + "automatic-transition=true",
                        PREFIX + "max-concurrent-calls=5")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    OrderInventoryResilienceProperties properties =
                            context.getBean(OrderInventoryResilienceProperties.class);
                    assertThat(properties.slidingWindowSize()).isEqualTo(12);
                    assertThat(properties.minimumNumberOfCalls()).isEqualTo(6);
                    assertThat(properties.failureRateThreshold()).isEqualTo(40.0f);
                    assertThat(properties.waitDurationInOpenState()).isEqualTo(Duration.ofSeconds(7));
                    assertThat(properties.permittedCallsInHalfOpenState()).isEqualTo(3);
                    assertThat(properties.automaticTransition()).isTrue();
                    assertThat(properties.maxConcurrentCalls()).isEqualTo(5);
                    assertThat(context.getBean(Bulkhead.class).getBulkheadConfig().getMaxWaitDuration())
                            .isZero();
                });
    }

    @Test
    void rejectsAMinimumCallCountLargerThanTheSlidingWindow() {
        contextRunner.withPropertyValues(
                        PREFIX + "sliding-window-size=10",
                        PREFIX + "minimum-number-of-calls=11")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsNonPositiveDurationsAndLimits() {
        contextRunner.withPropertyValues(
                        PREFIX + "wait-duration-in-open-state=0ms",
                        PREFIX + "max-concurrent-calls=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
