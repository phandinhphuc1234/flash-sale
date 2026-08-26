package com.philia.flashsale.order.configuration;

import com.philia.flashsale.order.observability.OrderObservability;
import com.philia.flashsale.order.observability.OrderReadinessHealthIndicator;
import java.sql.Connection;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

/** Wires Order readiness to PostgreSQL and exposes broker/outbox state as diagnostics only. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({DataSource.class, JdbcTemplate.class})
public class OrderReadinessConfiguration {

    @Bean("orderReadiness")
    HealthIndicator orderReadiness(DataSource dataSource, JdbcTemplate jdbc,
            ObjectProvider<KafkaListenerEndpointRegistry> listeners, OrderObservability observability) {
        return new OrderReadinessHealthIndicator(
                () -> postgresAvailable(dataSource),
                () -> consumerAvailable(listeners.getIfAvailable()),
                () -> outboxBacklog(jdbc),
                () -> oldestPendingAge(jdbc),
                () -> sagaStateCounts(jdbc),
                () -> oldestSagaStepAge(jdbc),
                observability);
    }

    static boolean postgresAvailable(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(1);
            try (var result = statement.executeQuery("select 1")) {
                return result.next() && result.getInt(1) == 1;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean consumerAvailable(KafkaListenerEndpointRegistry registry) {
        if (registry == null) {
            return true;
        }
        Collection<?> containers = registry.getListenerContainers();
        return containers.isEmpty() || registry.getListenerContainers().stream()
                .allMatch(container -> ((org.springframework.kafka.listener.MessageListenerContainer) container).isRunning());
    }

    private static long outboxBacklog(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject(
                "select count(*) from order_outbox_events where status <> 'PUBLISHED'", Long.class);
        return count == null ? 0L : count;
    }

    private static Duration oldestPendingAge(JdbcTemplate jdbc) {
        Number seconds = jdbc.queryForObject(
                "select coalesce(extract(epoch from (current_timestamp - min(next_attempt_at))), 0) "
                        + "from order_outbox_events where status <> 'PUBLISHED'",
                Number.class);
        return seconds == null ? Duration.ZERO : Duration.ofMillis(Math.max(0L,
                Math.round(seconds.doubleValue() * 1000d)));
    }

    private static Map<String, Long> sagaStateCounts(JdbcTemplate jdbc) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "select status, count(*) as state_count from purchase_sagas group by status")) {
            Object count = row.get("state_count");
            counts.put(String.valueOf(row.get("status")), count instanceof Number number
                    ? number.longValue() : 0L);
        }
        return Map.copyOf(counts);
    }

    private static Duration oldestSagaStepAge(JdbcTemplate jdbc) {
        Number seconds = jdbc.queryForObject(
                "select coalesce(extract(epoch from (current_timestamp - min(step_started_at))), 0) "
                        + "from purchase_sagas where status not in ('COMPLETED', 'COMPENSATED')",
                Number.class);
        return seconds == null ? Duration.ZERO : Duration.ofMillis(Math.max(0L,
                Math.round(seconds.doubleValue() * 1000d)));
    }
}
