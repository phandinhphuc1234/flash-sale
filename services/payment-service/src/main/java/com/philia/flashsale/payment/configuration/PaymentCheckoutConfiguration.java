package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.payment.application.port.in.StartCheckoutUseCase;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClientIdempotencyPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentRecoveryWorkPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.CheckoutPersistenceService;
import com.philia.flashsale.payment.payment.application.service.StartCheckoutService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for the durable owner Checkout use case. */
@Configuration
@ConditionalOnProperty(name = {"payment.checkout.enabled", "payment.acceptance.enabled",
        "payment.stripe.enabled"}, havingValue = "true")
public class PaymentCheckoutConfiguration {

    @Bean
    CheckoutPersistenceService checkoutPersistenceService(LoadPaymentPort payments,
            SavePaymentPort paymentWriter, PaymentClientIdempotencyPort idempotency,
            PaymentRecoveryWorkPort recovery, PaymentClockPort clock, PaymentIdentityPort identities,
            PaymentTransactionPort transactions, PaymentRecoveryProperties recoveryProperties,
            StripeCheckoutProperties stripeProperties) {
        return new CheckoutPersistenceService(payments, paymentWriter, idempotency, recovery, clock,
                identities, transactions, recoveryProperties.safeReplayWindow(),
                stripeProperties.successUrl().toString(), stripeProperties.cancelUrl().toString());
    }

    @Bean
    StartCheckoutUseCase startCheckoutUseCase(CheckoutPersistenceService persistence,
            HostedCheckoutProviderPort provider) {
        return new StartCheckoutService(persistence, provider);
    }
}
