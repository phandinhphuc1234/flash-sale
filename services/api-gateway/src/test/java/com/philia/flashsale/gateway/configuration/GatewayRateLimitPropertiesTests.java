package com.philia.flashsale.gateway.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import com.philia.flashsale.gateway.ratelimit.RateLimitPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

class GatewayRateLimitPropertiesTests {

    private static final long LUA_MAX_EXACT_INTEGER = 9_007_199_254_740_991L;
    private static final String VALID_BASE64_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void disabledConfigurationDoesNotRequireHmacSecretOrRedisPolicy() {
        contextRunner
                .withPropertyValues(
                        "flashsale.gateway.rate-limit.enabled=false",
                        "flashsale.gateway.rate-limit.environment=local")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    GatewayRateLimitProperties properties =
                            context.getBean(GatewayRateLimitProperties.class);
                    assertThat(properties.enabled()).isFalse();
                    assertThat(properties.hmacSecret()).isBlank();
                    assertThat(properties.validatedPolicies()).isEmpty();
                });
    }

    @ParameterizedTest
    @MethodSource("validEnvironments")
    void acceptsOnlyTheApprovedEnvironmentIdentifierFormat(String environment) {
        contextRunner
                .withPropertyValues(standardEnabledPolicy(environment))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GatewayRateLimitProperties.class).environment())
                            .isEqualTo(environment);
                });
    }

    @ParameterizedTest
    @MethodSource("invalidEnvironments")
    void rejectsEnvironmentIdentifiersOutsideTheApprovedRegex(String environment) {
        contextRunner
                .withPropertyValues(standardEnabledPolicy(environment))
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @MethodSource("invalidStateVersions")
    void rejectsPolicyStateVersionsOutsideTheApprovedPattern(String stateVersion) {
        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .withPropertyValues(policy("public-catalog-read", "state-version", stateVersion))
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @MethodSource("invalidSelectorValues")
    void rejectsSelectorsOutsideTheSingleApprovedCatalogReadPolicy(
            String propertyName,
            String invalidValue) {
        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .withPropertyValues(policy("public-catalog-read", propertyName, invalidValue))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void derivesTheExactMvpLuaArgumentsAndTtlFromTheApprovedPolicy() {
        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    List<RateLimitPolicy> policies = context.getBean(GatewayRateLimitProperties.class)
                            .validatedPolicies();
                    assertThat(policies).hasSize(1);

                    RateLimitPolicy policy = policies.getFirst();
                    assertThat(policy.id()).isEqualTo("public-catalog-read");
                    assertThat(policy.stateVersion()).isEqualTo("p1");
                    assertThat(policy.routeId()).isEqualTo("product-catalog");
                    assertThat(policy.methods()).containsExactly(HttpMethod.GET);
                    assertThat(policy.identityStrategy()).isEqualTo("CLIENT_IP");
                    assertThat(policy.failureMode()).isEqualTo("ALLOW_WITH_METRIC");
                    assertThat(policy.capacity()).isEqualTo(60);
                    assertThat(policy.refillTokens()).isEqualTo(30);
                    assertThat(policy.refillPeriodMs()).isEqualTo(1_000);
                    assertThat(policy.requestCost()).isEqualTo(1);

                    assertThat(policy.capacityCredit()).isEqualTo(60_000);
                    assertThat(policy.requestCostCredit()).isEqualTo(1_000);
                    assertThat(policy.fullRefillMs()).isEqualTo(2_000);
                    assertThat(policy.stateTtlMs()).isEqualTo(60_000);
                    assertThat(policy.luaArguments())
                            .containsExactly("60000", "30", "1000", "2000", "60000");
                });
    }

    @ParameterizedTest
    @MethodSource("unsafeNumericPolicyValues")
    void rejectsAnyNumericLuaArgumentThatCannotBeRepresentedExactlyByRedisLua(
            String propertyName,
            String unsafeValue) {
        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .withPropertyValues(policy("public-catalog-read", propertyName, unsafeValue))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsCheckedDerivationThatWouldOverflowTheRedisLuaExactIntegerBoundary() {
        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .withPropertyValues(
                        policy("public-catalog-read", "capacity", "4503599627370496"),
                        policy("public-catalog-read", "refill-period", "2ms"),
                        policy("public-catalog-read", "request-cost", "1"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void rejectsPoliciesWhoseFullRefillDurationExceedsTwentyFourHours() {
        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .withPropertyValues(
                        policy("public-catalog-read", "capacity", "86400001"),
                        policy("public-catalog-read", "refill-tokens", "1"),
                        policy("public-catalog-read", "refill-period", "1s"),
                        policy("public-catalog-read", "request-cost", "1"))
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsTheLargestExactLuaIntegerWhenItIsAlreadyAValidDerivedArgument() {
        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .withPropertyValues(
                        policy("public-catalog-read", "capacity", Long.toString(LUA_MAX_EXACT_INTEGER)),
                        policy("public-catalog-read", "refill-tokens", Long.toString(LUA_MAX_EXACT_INTEGER)),
                        policy("public-catalog-read", "refill-period", "1ms"),
                        policy("public-catalog-read", "request-cost", "1"))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void enabledConfigurationRequiresAStandardBase64SecretWithAtLeastThirtyTwoBytes() {
        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .withPropertyValues("flashsale.gateway.rate-limit.hmac-secret=short")
                .run(context -> assertThat(context).hasFailed());

        contextRunner
                .withPropertyValues(standardEnabledPolicy("local"))
                .withPropertyValues("flashsale.gateway.rate-limit.hmac-secret=" + VALID_BASE64_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(GatewayRateLimitProperties.class).decodedHmacSecret())
                            .hasSize(32);
                });
    }

    private static Stream<String> validEnvironments() {
        return Stream.of("l", "local", "dev-1", "flash-sale-edge-local-2026");
    }

    private static Stream<String> invalidEnvironments() {
        return Stream.of(
                "",
                "Local",
                "-local",
                "local-",
                "local_env",
                "local.env",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
    }

    private static Stream<String> invalidStateVersions() {
        return Stream.of("", "1", "p0", "p01", "p1000000000", "P1", "p1-dev");
    }

    private static Stream<Arguments> invalidSelectorValues() {
        return Stream.of(
                Arguments.of("route-id", "product-catalog-admin"),
                Arguments.of("methods[0]", "POST"),
                Arguments.of("identity-strategy", "USER_ID"),
                Arguments.of("failure-mode", "FAIL_CLOSED"));
    }

    private static Stream<Arguments> unsafeNumericPolicyValues() {
        String aboveLuaMax = "9007199254740992";

        return Stream.of(
                Arguments.of("capacity", aboveLuaMax),
                Arguments.of("refill-tokens", aboveLuaMax),
                Arguments.of("request-cost", aboveLuaMax));
    }

    private static String[] standardEnabledPolicy(String environment) {
        return new String[] {
            "flashsale.gateway.rate-limit.enabled=true",
            "flashsale.gateway.rate-limit.environment=" + environment,
            "flashsale.gateway.rate-limit.key-prefix=rl",
            "flashsale.gateway.rate-limit.command-timeout=50ms",
            "flashsale.gateway.rate-limit.hmac-secret=" + VALID_BASE64_SECRET,
            policy("public-catalog-read", "enabled", "true"),
            policy("public-catalog-read", "state-version", "p1"),
            policy("public-catalog-read", "route-id", "product-catalog"),
            policy("public-catalog-read", "methods[0]", "GET"),
            policy("public-catalog-read", "identity-strategy", "CLIENT_IP"),
            policy("public-catalog-read", "capacity", "60"),
            policy("public-catalog-read", "refill-tokens", "30"),
            policy("public-catalog-read", "refill-period", "1s"),
            policy("public-catalog-read", "request-cost", "1"),
            policy("public-catalog-read", "failure-mode", "ALLOW_WITH_METRIC")
        };
    }

    private static String policy(String policyId, String propertyName, String value) {
        return "flashsale.gateway.rate-limit.policies.%s.%s=%s"
                .formatted(policyId, propertyName, value);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(GatewayRateLimitProperties.class)
    static class TestConfiguration {
    }
}
