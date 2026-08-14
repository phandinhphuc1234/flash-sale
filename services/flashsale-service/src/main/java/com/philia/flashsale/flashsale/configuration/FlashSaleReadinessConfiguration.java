package com.philia.flashsale.flashsale.configuration;

import com.philia.flashsale.flashsale.observability.FlashSaleReadinessHealthIndicator;
import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/** Wires external readiness probes while keeping the indicator free of provider APIs. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean({DataSource.class, RedisConnectionFactory.class})
class FlashSaleReadinessConfiguration {

    @Bean("flashSaleReadiness")
    HealthIndicator flashSaleReadiness(DataSource dataSource, RedisConnectionFactory redis) {
        return new FlashSaleReadinessHealthIndicator(
                () -> postgresAvailable(dataSource),
                () -> redisAvailable(redis));
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
}
