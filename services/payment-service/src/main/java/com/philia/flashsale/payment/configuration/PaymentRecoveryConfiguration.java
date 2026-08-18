package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.observability.PaymentRecoveryObservability;
import com.philia.flashsale.payment.payment.application.port.in.ReconcilePaymentUseCase;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadDuePaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.PaymentRecoveryPolicy;
import com.philia.flashsale.payment.payment.application.service.ReconcilePaymentService;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition root for the disabled-by-default reconciliation and deadline workers. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = {"payment.acceptance.enabled", "payment.checkout.enabled",
        "payment.stripe.enabled", "payment.recovery.enabled"}, havingValue = "true")
public class PaymentRecoveryConfiguration {

    @Bean
    PaymentRecoveryPolicy paymentRecoveryPolicy(PaymentRecoveryProperties properties) {
        return new PaymentRecoveryPolicy(properties.retryBackoff(), properties.maxAttempts(),
                properties.safeReplayWindow());
    }

    @Bean
    PaymentRecoveryObservability paymentRecoveryObservability(MeterRegistry registry) {
        return new PaymentRecoveryObservability(registry);
    }

    @Bean
    ReconcilePaymentUseCase reconcilePaymentUseCase(PaymentRecoveryWorkPort recovery,
            LoadPaymentPort payments, SavePaymentPort paymentWriter,
            HostedCheckoutProviderPort provider, PaymentClockPort clock,
            PaymentTransactionPort transactions, SavePaymentOutboxPort outbox,
            LoadDuePaymentPort duePayments, PaymentIdentityPort identities,
            PaymentRecoveryPolicy policy, PaymentRecoveryProperties recoveryProperties,
            StripeCheckoutProperties stripeProperties) {
        return new ReconcilePaymentService(recovery, payments, paymentWriter, provider, clock,
                transactions, outbox, duePayments, identities, policy, recoveryProperties.batchSize(),
                recoveryProperties.claimLease(), stripeProperties.successUrl().toString(),
                stripeProperties.cancelUrl().toString());
    }
}
