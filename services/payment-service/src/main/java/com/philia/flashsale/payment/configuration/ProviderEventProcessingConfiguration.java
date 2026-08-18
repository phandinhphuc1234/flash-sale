package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.payment.adapter.in.webhook.StripeWebhookMapper;
import com.philia.flashsale.payment.payment.adapter.in.webhook.StripeWebhookVerifier;
import com.philia.flashsale.payment.payment.application.port.in.AcceptProviderEventUseCase;
import com.philia.flashsale.payment.payment.application.port.in.ProcessProviderEventUseCase;
import com.philia.flashsale.payment.payment.application.port.out.HostedCheckoutProviderPort;
import com.philia.flashsale.payment.payment.application.port.out.LoadPaymentPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentClockPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentIdentityPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentProviderReceiptPort;
import com.philia.flashsale.payment.payment.application.port.out.PaymentTransactionPort;
import com.philia.flashsale.payment.payment.application.port.out.SavePaymentPort;
import com.philia.flashsale.payment.payment.application.service.AcceptProviderEventService;
import com.philia.flashsale.payment.payment.application.service.ProcessProviderEventService;
import com.philia.flashsale.payment.outbox.application.port.SavePaymentOutboxPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition root for verified webhook receipt acceptance and asynchronous convergence. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = {"payment.acceptance.enabled", "payment.checkout.enabled",
        "payment.stripe.enabled"}, havingValue = "true")
public class ProviderEventProcessingConfiguration {

    @Bean
    StripeWebhookMapper stripeWebhookMapper() {
        return new StripeWebhookMapper();
    }

    @Bean
    StripeWebhookVerifier stripeWebhookVerifier(StripeCheckoutProperties properties,
            StripeWebhookMapper mapper) {
        return new StripeWebhookVerifier(properties, mapper);
    }

    @Bean
    AcceptProviderEventUseCase acceptProviderEventUseCase(PaymentProviderReceiptPort receipts,
            PaymentClockPort clock, PaymentIdentityPort identities, PaymentTransactionPort transactions) {
        return new AcceptProviderEventService(receipts, clock, identities, transactions);
    }

    @Bean
    ProcessProviderEventUseCase processProviderEventUseCase(PaymentProviderReceiptPort receipts,
            LoadPaymentPort payments, SavePaymentPort paymentWriter, SavePaymentOutboxPort outbox,
            HostedCheckoutProviderPort provider, PaymentClockPort clock, PaymentTransactionPort transactions,
            ProviderEventProcessingProperties properties) {
        return new ProcessProviderEventService(receipts, payments, paymentWriter, outbox, provider,
                clock, transactions, properties.batchSize(), properties.claimLease(), properties.maxAttempts(),
                properties.retryBackoff());
    }
}
