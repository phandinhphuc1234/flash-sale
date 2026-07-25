package com.philia.flashsale.gateway.filter.global;

import com.philia.flashsale.gateway.error.GatewayHttpErrorWriter;
import com.philia.flashsale.gateway.ratelimit.DistributedRateLimiter;
import com.philia.flashsale.gateway.ratelimit.RateLimitCoordinatorException;
import com.philia.flashsale.gateway.ratelimit.RateLimitDecision;
import com.philia.flashsale.gateway.ratelimit.RateLimitIdentityResolver;
import com.philia.flashsale.gateway.ratelimit.RateLimitPolicy;
import com.philia.flashsale.gateway.ratelimit.RateLimitPolicyResolver;
import java.util.Objects;
import java.util.Optional;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Applies the distributed rate-limit policy selected for an already matched Gateway route.
 */
public final class GatewayRateLimitGlobalFilter implements GlobalFilter, Ordered {

    private final RateLimitPolicyResolver policyResolver;
    private final RateLimitIdentityResolver identityResolver;
    private final DistributedRateLimiter limiter;
    private final GatewayHttpErrorWriter errorWriter;

    public GatewayRateLimitGlobalFilter(
            RateLimitPolicyResolver policyResolver,
            RateLimitIdentityResolver identityResolver,
            DistributedRateLimiter limiter,
            GatewayHttpErrorWriter errorWriter) {
        this.policyResolver = Objects.requireNonNull(policyResolver, "policy resolver must not be null");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identity resolver must not be null");
        this.limiter = Objects.requireNonNull(limiter, "distributed rate limiter must not be null");
        this.errorWriter = Objects.requireNonNull(errorWriter, "gateway error writer must not be null");
    }

    @Override
    public int getOrder() {
        return RouteToRequestUrlFilter.ROUTE_TO_URL_FILTER_ORDER - 1;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return resolvePolicy(exchange)
                .map(policy -> applyPolicy(exchange, chain, policy))
                .orElseGet(() -> chain.filter(exchange));
    }

    private Mono<Void> applyPolicy(ServerWebExchange exchange, GatewayFilterChain chain, RateLimitPolicy policy) {
        Optional<RateLimitIdentityResolver.ResolvedRateLimitIdentity> resolvedIdentity =
                identityResolver.resolve(exchange, policy);
        if (resolvedIdentity.isEmpty()) {
            return chain.filter(exchange);
        }

        return limiter.acquire(policy, resolvedIdentity.get().bucketKey())
                .flatMap(decision -> routeDecision(exchange, chain, decision))
                .onErrorResume(RateLimitCoordinatorException.class, ignored -> chain.filter(exchange));
    }

    private Mono<Void> routeDecision(
            ServerWebExchange exchange,
            GatewayFilterChain chain,
            RateLimitDecision decision) {
        if (decision.rejected()) {
            return errorWriter.writeRateLimitExceeded(exchange, decision.retryAfter());
        }
        return chain.filter(exchange);
    }

    private Optional<RateLimitPolicy> resolvePolicy(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return Optional.empty();
        }
        return policyResolver.resolve(route.getId(), exchange.getRequest().getMethod());
    }
}
