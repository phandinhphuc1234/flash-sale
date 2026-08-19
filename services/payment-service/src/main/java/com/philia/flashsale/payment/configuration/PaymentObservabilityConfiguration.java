package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.observability.PaymentObservability;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Framework-only composition for the Payment telemetry facade. */
@Configuration(proxyBeanMethods = false)
public class PaymentObservabilityConfiguration {

    @Bean
    PaymentObservability paymentObservability(MeterRegistry registry) {
        return new PaymentObservability(registry);
    }
}
