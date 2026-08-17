package com.philia.flashsale.payment.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Binding and validation guards for the G1 service configuration boundary. */
class PaymentConfigurationPropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class);

    @Test
    void bindsApprovedTopicsRetriesAndDisabledWorkerDefaults() {
        contextRunner.withPropertyValues(
                "payment.provider=STRIPE",
                "payment.public-api-prefix=/api/v1/payments",
                "payment.webhook-path=/webhooks/v1/payments/stripe",
                "payment.jwt-issuer=http://localhost:8080",
                "payment.jwt-jwk-set-uri=http://localhost:8080/.well-known/jwks.json",
                "payment.jwt-audience=flash-sale-api",
                "payment.jwt-type=at+jwt",
                "payment.stripe.mode=test",
                "payment.stripe.api-base-url=https://api.stripe.com",
                "payment.stripe.expected-api-version=2026-07-29.dahlia",
                "payment.stripe.webhook-tolerance=5m",
                "payment.stripe.connect-timeout=5s",
                "payment.stripe.read-timeout=20s",
                "payment.stripe.success-url=http://localhost:3000/success",
                "payment.stripe.cancel-url=http://localhost:3000/cancel",
                "payment.kafka.bootstrap-servers=localhost:9092",
                "payment.kafka.schema-registry-url=http://localhost:8081",
                "payment.kafka.command-topic=flashsale.payment.commands.v1",
                "payment.kafka.event-topic=flashsale.payment.events.v1",
                "payment.kafka.command-dlt-topic=flashsale.payment.payment-requested.dlt.v1",
                "payment.kafka.consumer-group=payment-service-v1",
                "payment.kafka.retry-delays=1s,3s,10s",
                "payment.kafka.outbox-poll-interval=500ms",
                "payment.kafka.outbox-batch-size=100",
                "payment.kafka.outbox-claim-lease=30s",
                "payment.kafka.outbox-retry-backoff-cap=60s",
                "payment.recovery.poll-interval=1s",
                "payment.recovery.batch-size=100",
                "payment.recovery.claim-lease=30s",
                "payment.recovery.max-attempts=10",
                "payment.recovery.retry-backoff=1s,3s,10s,30s,60s",
                "payment.recovery.safe-replay-window=23h")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(PaymentKafkaProperties.class).commandTopic())
                            .isEqualTo("flashsale.payment.commands.v1");
                    assertThat(context.getBean(PaymentKafkaProperties.class).retryDelays())
                            .hasSize(3);
                    assertThat(context.getBean(PaymentRecoveryProperties.class).safeReplayWindow())
                            .hasHours(23);
                    assertThat(context.getBean(StripeCheckoutProperties.class).enabled())
                            .isFalse();
                });
    }

    @Test
    void rejectsEnabledStripeWithoutCredentials() {
        contextRunner.withPropertyValues(
                "payment.provider=STRIPE",
                "payment.public-api-prefix=/api/v1/payments",
                "payment.webhook-path=/webhooks/v1/payments/stripe",
                "payment.jwt-issuer=http://localhost:8080",
                "payment.jwt-jwk-set-uri=http://localhost:8080/.well-known/jwks.json",
                "payment.jwt-audience=flash-sale-api",
                "payment.jwt-type=at+jwt",
                "payment.stripe.enabled=true",
                "payment.stripe.mode=test",
                "payment.stripe.api-base-url=https://api.stripe.com",
                "payment.stripe.expected-api-version=2026-07-29.dahlia",
                "payment.stripe.webhook-tolerance=5m",
                "payment.stripe.connect-timeout=5s",
                "payment.stripe.read-timeout=20s",
                "payment.stripe.success-url=http://localhost:3000/success",
                "payment.stripe.cancel-url=http://localhost:3000/cancel",
                "payment.kafka.bootstrap-servers=localhost:9092",
                "payment.kafka.schema-registry-url=http://localhost:8081",
                "payment.kafka.command-topic=flashsale.payment.commands.v1",
                "payment.kafka.event-topic=flashsale.payment.events.v1",
                "payment.kafka.command-dlt-topic=flashsale.payment.payment-requested.dlt.v1",
                "payment.kafka.consumer-group=payment-service-v1",
                "payment.kafka.retry-delays=1s,3s,10s",
                "payment.kafka.outbox-poll-interval=500ms",
                "payment.kafka.outbox-batch-size=100",
                "payment.kafka.outbox-claim-lease=30s",
                "payment.kafka.outbox-retry-backoff-cap=60s",
                "payment.recovery.poll-interval=1s",
                "payment.recovery.batch-size=100",
                "payment.recovery.claim-lease=30s",
                "payment.recovery.max-attempts=10",
                "payment.recovery.retry-backoff=1s,3s,10s,30s,60s",
                "payment.recovery.safe-replay-window=23h")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({
            PaymentProperties.class,
            StripeCheckoutProperties.class,
            PaymentKafkaProperties.class,
            PaymentRecoveryProperties.class
    })
    static class BindingConfiguration {
    }
}
