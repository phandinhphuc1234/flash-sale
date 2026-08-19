package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.observability.PaymentReadinessHealthIndicator;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition-only wiring for the Payment readiness signal. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(DataSource.class)
public class PaymentReadinessConfiguration {

    @Bean(name = "paymentReadiness")
    PaymentReadinessHealthIndicator paymentReadiness(DataSource dataSource,
            PaymentKafkaProperties kafka, StripeCheckoutProperties stripe,
            PaymentRecoveryProperties recovery) {
        return new PaymentReadinessHealthIndicator(dataSource, kafka, stripe, recovery);
    }
}
