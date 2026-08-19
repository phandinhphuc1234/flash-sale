package com.philia.flashsale.payment.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Runtime contract checks for the pinned Stripe webhook API version and JVM DNS cache policy. */
class StripeRuntimePlatformContractTests {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void pinsTheStripeApiVersionUsedByTheSdkAndExampleEnvironment() throws Exception {
        String application = Files.readString(ROOT.resolve("payment-service/src/main/resources/application.yml"));
        String example = Files.readString(ROOT.resolve("../infra/docker/.env.example"));

        assertThat(application).contains("expected-api-version: ${STRIPE_API_VERSION:2026-07-29.dahlia}");
        assertThat(example).contains("STRIPE_API_VERSION=2026-07-29.dahlia");
    }

    @Test
    void pinsPaymentContainerDnsCacheToSixtySeconds() throws Exception {
        String compose = Files.readString(ROOT.resolve("../infra/docker/compose.yml"));
        String example = Files.readString(ROOT.resolve("../infra/docker/.env.example"));

        assertThat(compose).contains("-Dnetworkaddress.cache.ttl=60");
        assertThat(example).contains("PAYMENT_JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0 -Dnetworkaddress.cache.ttl=60");
    }
}
