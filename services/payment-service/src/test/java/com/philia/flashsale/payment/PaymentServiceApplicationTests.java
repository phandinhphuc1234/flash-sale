package com.philia.flashsale.payment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "payment.provider=STRIPE",
        "payment.public-api-prefix=/api/v1/payments",
        "payment.webhook-path=/webhooks/v1/payments/stripe",
        "payment.jwt-issuer=http://localhost:8080",
        "payment.jwt-jwk-set-uri=http://localhost:8080/.well-known/jwks.json",
        "payment.jwt-audience=flash-sale-api",
        "payment.jwt-type=at+jwt",
        "payment.stripe.api-base-url=https://api.stripe.com",
        "payment.stripe.secret-key=sk_test_context_only",
        "payment.stripe.publishable-key=pk_test_context_only",
        "payment.stripe.webhook-secret=whsec_context_only",
        "payment.stripe.expected-api-version=2026-07-29.dahlia",
        "payment.stripe.success-url=http://localhost:3000/payments/success",
        "payment.stripe.cancel-url=http://localhost:3000/payments/cancel",
        "payment.kafka.bootstrap-servers=localhost:9092",
        "payment.kafka.schema-registry-url=http://localhost:8081",
        "payment.kafka.retry-delays=1s,3s,10s",
        "payment.kafka.outbox-poll-interval=500ms",
        "payment.kafka.outbox-claim-lease=30s",
        "payment.kafka.outbox-retry-backoff-cap=60s",
        "payment.recovery.poll-interval=1s",
        "payment.recovery.claim-lease=30s",
        "payment.recovery.retry-backoff=1s,3s,10s,30s,60s",
        "payment.recovery.safe-replay-window=23h",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "liquibase.integration.spring.boot3.autoconfigure.LiquibaseAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
})
class PaymentServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
