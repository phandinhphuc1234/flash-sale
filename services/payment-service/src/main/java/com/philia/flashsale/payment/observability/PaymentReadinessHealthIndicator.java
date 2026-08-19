package com.philia.flashsale.payment.observability;

import com.philia.flashsale.payment.configuration.PaymentKafkaProperties;
import com.philia.flashsale.payment.configuration.PaymentRecoveryProperties;
import com.philia.flashsale.payment.configuration.StripeCheckoutProperties;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Readiness signal for durable Payment storage and configuration.
 *
 * <p>PostgreSQL is the only gating dependency. Kafka, Schema Registry, Stripe, recovery, and
 * outbox are exposed as component details so operators can see degraded capabilities without
 * making durable owner queries unavailable during a broker/provider outage.
 */
public final class PaymentReadinessHealthIndicator implements HealthIndicator {
    private final DataSource dataSource;
    private final PaymentKafkaProperties kafka;
    private final StripeCheckoutProperties stripe;
    private final PaymentRecoveryProperties recovery;

    public PaymentReadinessHealthIndicator(DataSource dataSource, PaymentKafkaProperties kafka,
            StripeCheckoutProperties stripe, PaymentRecoveryProperties recovery) {
        this.dataSource = dataSource;
        this.kafka = kafka;
        this.stripe = stripe;
        this.recovery = recovery;
    }

    @Override
    public Health health() {
        boolean postgresReady = postgresReady();
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("postgresql", postgresReady ? "UP" : "DOWN");
        details.put("kafka", kafkaState());
        details.put("schemaRegistry", schemaRegistryState());
        details.put("stripe", stripeState());
        details.put("recovery", recovery.enabled() ? "ENABLED" : "DISABLED");
        details.put("outbox", kafka.outboxPublisherEnabled() ? "ENABLED" : "DISABLED");

        Health.Builder builder = postgresReady ? Health.up() : Health.down();
        details.forEach(builder::withDetail);
        return builder.build();
    }

    private boolean postgresReady() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception exception) {
            return false;
        }
    }

    private String kafkaState() {
        return kafka.consumerEnabled() || kafka.outboxPublisherEnabled() ? "CONFIGURED" : "DISABLED";
    }

    private String schemaRegistryState() {
        return kafka.schemaRegistryUrl() != null ? "CONFIGURED" : "DISABLED";
    }

    private String stripeState() {
        if (!stripe.enabled()) {
            return "DISABLED";
        }
        boolean credentials = hasText(stripe.secretKey()) && hasText(stripe.webhookSecret());
        return credentials && stripe.apiBaseUrl() != null ? "CONFIGURED" : "MISCONFIGURED";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
