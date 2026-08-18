package com.philia.flashsale.payment.configuration;

import com.philia.flashsale.payment.payment.adapter.out.provider.stripe.StripeCheckoutMapper;
import com.philia.flashsale.payment.payment.adapter.out.provider.stripe.StripeFailureClassifier;
import com.stripe.StripeClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root for the pinned Stripe SDK; secrets remain configuration-only. */
@Configuration
@ConditionalOnProperty(name = "payment.checkout.enabled", havingValue = "true")
public class StripeClientConfiguration {

    @Bean
    @ConditionalOnProperty(name = "payment.stripe.enabled", havingValue = "true")
    StripeClient stripeClient(StripeCheckoutProperties properties) {
        return StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setApiBase(properties.apiBaseUrl().toString())
                .setConnectTimeout((int) properties.connectTimeout().toMillis())
                .setReadTimeout((int) properties.readTimeout().toMillis())
                .setMaxNetworkRetries(properties.maxNetworkRetries())
                .build();
    }

    @Bean
    StripeCheckoutMapper stripeCheckoutMapper() {
        return new StripeCheckoutMapper();
    }

    @Bean
    StripeFailureClassifier stripeFailureClassifier() {
        return new StripeFailureClassifier();
    }
}
