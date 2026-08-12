package com.philia.flashsale.gateway.configuration;

import com.philia.flashsale.gateway.error.GatewayHttpErrorWriter;
import com.philia.flashsale.gateway.filter.global.GatewayRateLimitGlobalFilter;
import com.philia.flashsale.gateway.ratelimit.DirectClientIpRateLimitIdentityResolver;
import com.philia.flashsale.gateway.ratelimit.DistributedRateLimiter;
import com.philia.flashsale.gateway.ratelimit.HmacRateLimitBucketKeyFactory;
import com.philia.flashsale.gateway.ratelimit.RateLimitIdentityResolver;
import com.philia.flashsale.gateway.ratelimit.RateLimitPolicyResolver;
import com.philia.flashsale.gateway.ratelimit.redis.RedisTokenBucketRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;

/**
 * Wires Gateway rate-limit policy and coordinator beans without making Redis a
 * startup dependency while the limiter is disabled.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayRateLimitProperties.class)
public class GatewayRateLimitConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RateLimitPolicyResolver rateLimitPolicyResolver(GatewayRateLimitProperties properties) {
        return new RateLimitPolicyResolver(properties.validatedPolicies());
    }

    @Bean
    @ConditionalOnMissingBean(DistributedRateLimiter.class)
    @ConditionalOnProperty(prefix = "flashsale.gateway.rate-limit", name = "enabled", havingValue = "true")
    DistributedRateLimiter distributedRateLimiter(
            ReactiveStringRedisTemplate redisTemplate,
            GatewayRateLimitProperties properties) {
        return new RedisTokenBucketRateLimiter(redisTemplate, properties.commandTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "flashsale.gateway.rate-limit", name = "enabled", havingValue = "true")
    HmacRateLimitBucketKeyFactory hmacRateLimitBucketKeyFactory(GatewayRateLimitProperties properties) {
        return new HmacRateLimitBucketKeyFactory(
                properties.keyPrefix(),
                properties.environment(),
                properties.decodedHmacSecret());
    }

    @Bean
    @ConditionalOnMissingBean(RateLimitIdentityResolver.class)
    @ConditionalOnProperty(prefix = "flashsale.gateway.rate-limit", name = "enabled", havingValue = "true")
    RateLimitIdentityResolver rateLimitIdentityResolver(HmacRateLimitBucketKeyFactory bucketKeyFactory) {
        return new DirectClientIpRateLimitIdentityResolver(bucketKeyFactory);
    }

    @Bean
    @ConditionalOnMissingBean(GatewayRateLimitGlobalFilter.class)
    @ConditionalOnProperty(prefix = "flashsale.gateway.rate-limit", name = "enabled", havingValue = "true")
    GatewayRateLimitGlobalFilter gatewayRateLimitGlobalFilter(
            RateLimitPolicyResolver policyResolver,
            RateLimitIdentityResolver identityResolver,
            DistributedRateLimiter distributedRateLimiter,
            GatewayHttpErrorWriter errorWriter) {
        return new GatewayRateLimitGlobalFilter(
                policyResolver,
                identityResolver,
                distributedRateLimiter,
                errorWriter);
    }
}
