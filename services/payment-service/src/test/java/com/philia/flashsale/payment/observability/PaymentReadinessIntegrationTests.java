package com.philia.flashsale.payment.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.philia.flashsale.payment.configuration.PaymentKafkaProperties;
import com.philia.flashsale.payment.configuration.PaymentRecoveryProperties;
import com.philia.flashsale.payment.configuration.StripeCheckoutProperties;
import java.net.URI;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

/** Readiness proof: PostgreSQL gates while broker/provider outages remain component detail. */
class PaymentReadinessIntegrationTests {

    @Test
    void reportsDatabaseDownAsNotReadyButDoesNotFailOnKafkaOrStripeOutage() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(false);

        PaymentReadinessHealthIndicator indicator = new PaymentReadinessHealthIndicator(
                dataSource, kafka(true), stripe(true), recovery(true));

        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("postgresql", "DOWN")
                .containsEntry("kafka", "CONFIGURED")
                .containsEntry("schemaRegistry", "CONFIGURED")
                .containsEntry("stripe", "CONFIGURED")
                .containsEntry("recovery", "ENABLED");
    }

    @Test
    void reportsHealthyDatabaseAndDisabledOptionalComponentsAsReady() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);

        var health = new PaymentReadinessHealthIndicator(
                dataSource, kafka(false), stripe(false), recovery(false)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("postgresql", "UP")
                .containsEntry("kafka", "DISABLED")
                .containsEntry("stripe", "DISABLED")
                .containsEntry("recovery", "DISABLED")
                .containsEntry("outbox", "DISABLED");
    }

    private static PaymentKafkaProperties kafka(boolean enabled) {
        return new PaymentKafkaProperties("localhost:9092", URI.create("http://localhost:8081"),
                "commands", "events", "dlt", "payment-test", enabled, enabled, false,
                List.of(Duration.ofSeconds(1)), Duration.ofMillis(500), 10,
                Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    private static StripeCheckoutProperties stripe(boolean enabled) {
        return new StripeCheckoutProperties(enabled, "test", URI.create("https://api.stripe.com"),
                enabled ? "sk_test_value" : "", enabled ? "pk_test_value" : "",
                enabled ? "whsec_value" : "", "2026-07-29.dahlia", Duration.ofMinutes(5),
                Duration.ofSeconds(5), Duration.ofSeconds(20), 2,
                URI.create("http://localhost/success"), URI.create("http://localhost/cancel"));
    }

    private static PaymentRecoveryProperties recovery(boolean enabled) {
        return new PaymentRecoveryProperties(enabled, Duration.ofSeconds(1), 100,
                Duration.ofSeconds(30), 10, List.of(Duration.ofSeconds(1)), Duration.ofHours(23));
    }
}
