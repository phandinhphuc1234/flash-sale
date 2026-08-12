package com.philia.flashsale.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class RateLimitPolicyResolverTests {

    @Test
    void resolvesOnlyTheExactApprovedProductCatalogGetSelector() {
        RateLimitPolicy policy = enabledPolicy(
                "public-catalog-read",
                "product-catalog",
                Set.of(HttpMethod.GET));

        RateLimitPolicyResolver resolver = new RateLimitPolicyResolver(List.of(policy));

        assertThat(resolver.resolve("product-catalog", HttpMethod.GET))
                .containsSame(policy);
        assertThat(resolver.resolve("product-catalog", HttpMethod.POST))
                .isEmpty();
        assertThat(resolver.resolve("product-catalog-admin", HttpMethod.GET))
                .isEmpty();
        assertThat(resolver.resolve("product-catalog", null))
                .isEmpty();
        assertThat(resolver.resolve(null, HttpMethod.GET))
                .isEmpty();
    }

    @Test
    void ignoresDisabledPoliciesWhenResolvingTraffic() {
        RateLimitPolicy disabledPolicy = policy(
                "public-catalog-read",
                "product-catalog",
                Set.of(HttpMethod.GET),
                false);

        RateLimitPolicyResolver resolver = new RateLimitPolicyResolver(List.of(disabledPolicy));

        assertThat(resolver.resolve("product-catalog", HttpMethod.GET))
                .isEmpty();
    }

    @Test
    void rejectsTwoEnabledPoliciesForTheSameRouteAndMethodSelector() {
        RateLimitPolicy first = enabledPolicy(
                "public-catalog-read",
                "product-catalog",
                Set.of(HttpMethod.GET));
        RateLimitPolicy duplicate = enabledPolicy(
                "public-catalog-read-duplicate",
                "product-catalog",
                Set.of(HttpMethod.GET));

        assertThatThrownBy(() -> new RateLimitPolicyResolver(List.of(first, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product-catalog")
                .hasMessageContaining("GET");
    }

    @Test
    void permitsDifferentSelectorsEvenWhenPoliciesShareTheSameRoute() {
        RateLimitPolicy getPolicy = enabledPolicy(
                "public-catalog-read",
                "product-catalog",
                Set.of(HttpMethod.GET));
        RateLimitPolicy headPolicy = enabledPolicy(
                "public-catalog-head",
                "product-catalog",
                Set.of(HttpMethod.HEAD));

        RateLimitPolicyResolver resolver = new RateLimitPolicyResolver(List.of(getPolicy, headPolicy));

        assertThat(resolver.resolve("product-catalog", HttpMethod.GET))
                .containsSame(getPolicy);
        assertThat(resolver.resolve("product-catalog", HttpMethod.HEAD))
                .containsSame(headPolicy);
    }

    private static RateLimitPolicy enabledPolicy(
            String id,
            String routeId,
            Set<HttpMethod> methods) {
        return policy(id, routeId, methods, true);
    }

    private static RateLimitPolicy policy(
            String id,
            String routeId,
            Set<HttpMethod> methods,
            boolean enabled) {
        return RateLimitPolicy.create(
                id,
                "p1",
                routeId,
                methods,
                "CLIENT_IP",
                60,
                30,
                Duration.ofSeconds(1),
                1,
                "ALLOW_WITH_METRIC",
                enabled);
    }
}
