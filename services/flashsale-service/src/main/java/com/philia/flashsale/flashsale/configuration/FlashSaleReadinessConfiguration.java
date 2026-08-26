package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.observability.FlashSaleReadinessHealthIndicator;
import java.sql.Connection;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import com.philia.flashsale.flashsale.observability.FlashSaleObservability;

/** Wires external readiness probes while keeping the indicator free of provider APIs. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "flashsale.runtime.enabled", havingValue = "true", matchIfMissing = true)
class FlashSaleReadinessConfiguration {

    @Bean("flashSaleReadiness")
    HealthIndicator flashSaleReadiness(DataSource dataSource, RedisConnectionFactory redis,
            JdbcTemplate jdbc, FlashSaleObservability observability) {
        return new FlashSaleReadinessHealthIndicator(
                () -> postgresAvailable(dataSource),
                () -> redisAvailable(redis),
                () -> count(jdbc, "select count(*) from flash_sale_outbox_events where status <> 'PUBLISHED'"),
                () -> age(jdbc, "select coalesce(extract(epoch from (current_timestamp - min(next_attempt_at))), 0) "
                        + "from flash_sale_outbox_events where status <> 'PUBLISHED'"),
                () -> count(jdbc, "select count(*) from flash_sale_reservations where status in "
                        + "('CONFIRMED', 'RELEASED', 'EXPIRED') and redis_reconciled_at is null"),
                () -> age(jdbc, "select coalesce(extract(epoch from (current_timestamp - min(finalized_at))), 0) "
                        + "from flash_sale_reservations where status in ('CONFIRMED', 'RELEASED', 'EXPIRED') "
                        + "and redis_reconciled_at is null"),
                observability);
    }

    private static boolean postgresAvailable(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean redisAvailable(RedisConnectionFactory redis) {
        try (RedisConnection connection = redis.getConnection()) {
            return "PONG".equalsIgnoreCase(connection.ping());
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long count(JdbcTemplate jdbc, String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static Duration age(JdbcTemplate jdbc, String sql) {
        Number seconds = jdbc.queryForObject(sql, Number.class);
        return seconds == null ? Duration.ZERO : Duration.ofMillis(Math.max(0L,
                Math.round(seconds.doubleValue() * 1000d)));
    }
}
