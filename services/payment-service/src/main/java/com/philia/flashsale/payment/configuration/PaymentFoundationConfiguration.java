package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.payment.adapter.out.persistence.jpa.mapper.PaymentPersistenceMapper;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Composition-only wiring for shared Payment foundation capabilities. */
@Configuration
public class PaymentFoundationConfiguration {

    @Bean
    PaymentPersistenceMapper paymentPersistenceMapper() {
        return new PaymentPersistenceMapper();
    }

    @Bean
    PaymentClockPort paymentClockPort() {
        Clock clock = Clock.systemUTC();
        return clock::instant;
    }

    @Bean
    PaymentIdentityPort paymentIdentityPort() {
        return UUID::randomUUID;
    }

    @Bean
    @ConditionalOnBean(PlatformTransactionManager.class)
    PaymentTransactionPort paymentTransactionPort(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        return new PaymentTransactionPort() {
            @Override
            public <T> T execute(java.util.function.Supplier<T> work) {
                return template.execute(status -> work.get());
            }
        };
    }
}
