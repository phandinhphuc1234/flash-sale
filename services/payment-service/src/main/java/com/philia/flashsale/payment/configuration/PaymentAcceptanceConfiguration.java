package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.payment.application.port.in.AcceptPaymentRequestUseCase;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentCommandInboxPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.AcceptPaymentRequestService;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Composition wiring for atomic PaymentRequested acceptance.
 *
 * <p>No Kafka listener is enabled here. The future G4 listener remains disabled by the existing
 * {@code payment.kafka.consumer-enabled=false} default and will invoke this application port only
 * after its own envelope, key, retry, and acknowledgement policy is implemented.
 */
@Configuration
@ConditionalOnProperty(name = "payment.acceptance.enabled", havingValue = "true")
public class PaymentAcceptanceConfiguration {

    @Bean
    AcceptPaymentRequestUseCase acceptPaymentRequestUseCase(PaymentCommandInboxPort inbox,
            LoadPaymentPort payments, SavePaymentPort paymentWriter, SavePaymentOutboxPort outbox,
            PaymentClockPort clock, PaymentIdentityPort identities, PaymentTransactionPort transactions,
            PaymentKafkaProperties kafkaProperties) {
        return new AcceptPaymentRequestService(inbox, payments, paymentWriter, outbox, clock, identities,
                transactions, kafkaProperties.eventTopic());
    }
}
