package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.OwnedPaymentQueryJpaAdapter;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.OwnedPaymentQueryJpaRepository;
import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.repository.PaymentAttemptJpaRepository;
import com.philia.flashsale.payment.payment.application.port.in.GetOwnedPaymentUseCase;
import com.philia.flashsale.payment.payment.application.service.PaymentQueryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for the PostgreSQL-only owner query capability. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentQueryConfiguration {
    @Bean
    OwnedPaymentQueryJpaAdapter ownedPaymentQueryJpaAdapter(OwnedPaymentQueryJpaRepository payments,
            PaymentAttemptJpaRepository attempts) {
        return new OwnedPaymentQueryJpaAdapter(payments, attempts);
    }

    @Bean
    GetOwnedPaymentUseCase getOwnedPaymentUseCase(OwnedPaymentQueryJpaAdapter adapter) {
        return new PaymentQueryService(adapter);
    }
}
