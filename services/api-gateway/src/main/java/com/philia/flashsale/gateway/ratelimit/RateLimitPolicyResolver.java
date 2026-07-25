package com.philia.flashsale.gateway.ratelimit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpMethod;

/**
 * Selects the enabled policy for an exact Gateway route and HTTP method.
 */
public final class RateLimitPolicyResolver {

    private final Map<Selector, RateLimitPolicy> policiesBySelector;

    public RateLimitPolicyResolver(List<RateLimitPolicy> policies) {
        Map<Selector, RateLimitPolicy> indexedPolicies = new HashMap<>();

        for (RateLimitPolicy policy : List.copyOf(policies)) {
            if (!policy.enabled()) {
                continue;
            }

            for (HttpMethod method : policy.methods()) {
                Selector selector = new Selector(policy.routeId(), method);
                RateLimitPolicy previous = indexedPolicies.putIfAbsent(selector, policy);
                if (previous != null) {
                    throw new IllegalArgumentException(
                            "Duplicate rate-limit policy selector for route "
                                    + policy.routeId()
                                    + " and method "
                                    + method);
                }
            }
        }

        this.policiesBySelector = Map.copyOf(indexedPolicies);
    }

    public Optional<RateLimitPolicy> resolve(String routeId, HttpMethod method) {
        if (routeId == null || method == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(policiesBySelector.get(new Selector(routeId, method)));
    }

    private record Selector(String routeId, HttpMethod method) {}
}
