package com.philia.flashsale.gateway.configuration;

import com.philia.flashsale.gateway.ratelimit.RateLimitPolicy;
import com.philia.flashsale.gateway.ratelimit.RateLimitPolicyResolver;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpMethod;

/**
 * Declarative rate-limit configuration for the Gateway.
 *
 * <p>At this stage the class only validates policy shape and derives script
 * arguments. Runtime Redis enforcement is implemented by later tasks.
 */
@ConfigurationProperties(prefix = "flashsale.gateway.rate-limit")
public record GatewayRateLimitProperties(
        boolean enabled,
        String environment,
        String keyPrefix,
        Duration commandTimeout,
        String hmacSecret,
        Map<String, PolicyProperties> policies) {

    private static final Pattern ENVIRONMENT_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{0,30}[a-z0-9])?$");
    private static final Pattern STATE_VERSION_PATTERN = Pattern.compile("^p[1-9][0-9]{0,8}$");

    private static final String DEFAULT_ENVIRONMENT = "local";
    private static final String DEFAULT_KEY_PREFIX = "rl";
    private static final Duration DEFAULT_COMMAND_TIMEOUT = Duration.ofMillis(50);
    private static final Set<String> APPROVED_POLICY_IDS = Set.of("public-catalog-read", "auth-login", "auth-refresh");
    private static final String MVP_FAILURE_MODE = "ALLOW_WITH_METRIC";
    private static final int MIN_HMAC_SECRET_BYTES = 32;

    public GatewayRateLimitProperties {
        environment = environment == null ? DEFAULT_ENVIRONMENT : environment;
        keyPrefix = keyPrefix == null ? DEFAULT_KEY_PREFIX : keyPrefix;
        commandTimeout = commandTimeout == null ? DEFAULT_COMMAND_TIMEOUT : commandTimeout;
        hmacSecret = hmacSecret == null ? "" : hmacSecret;
        policies = policies == null ? Map.of() : Map.copyOf(policies);

        validateGlobal(environment, keyPrefix, commandTimeout);

        if (enabled) {
            validateHmacSecret(hmacSecret);
            validatePolicies(policies);
        }
    }

    public List<RateLimitPolicy> validatedPolicies() {
        if (!enabled) {
            return List.of();
        }

        return validatePolicies(policies);
    }

    public byte[] decodedHmacSecret() {
        if (!enabled) {
            return new byte[0];
        }
        return decodeHmacSecret(hmacSecret);
    }

    private static void validateGlobal(String environment, String keyPrefix, Duration commandTimeout) {
        if (!ENVIRONMENT_PATTERN.matcher(environment).matches()) {
            throw new IllegalArgumentException(
                    "rate-limit environment must be 1-32 chars using lowercase letters, digits, and hyphen");
        }
        if (!DEFAULT_KEY_PREFIX.equals(keyPrefix)) {
            throw new IllegalArgumentException("rate-limit key prefix must be rl for the MVP policy");
        }
        if (!DEFAULT_COMMAND_TIMEOUT.equals(commandTimeout)) {
            throw new IllegalArgumentException("rate-limit command timeout must be 50ms for the MVP policy");
        }
    }

    private static void validateHmacSecret(String hmacSecret) {
        decodeHmacSecret(hmacSecret);
    }

    private static byte[] decodeHmacSecret(String hmacSecret) {
        if (hmacSecret == null || hmacSecret.isBlank()) {
            throw new IllegalArgumentException("rate-limit hmac-secret must be standard Base64 and at least 32 bytes");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(hmacSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "rate-limit hmac-secret must be standard Base64 and at least 32 bytes",
                    exception);
        }

        if (decoded.length < MIN_HMAC_SECRET_BYTES) {
            throw new IllegalArgumentException("rate-limit hmac-secret must be standard Base64 and at least 32 bytes");
        }
        return decoded;
    }

    private static List<RateLimitPolicy> validatePolicies(Map<String, PolicyProperties> policies) {
        List<RateLimitPolicy> validatedPolicies = policies.entrySet().stream()
                .map(entry -> entry.getValue().toPolicy(entry.getKey()))
                .filter(RateLimitPolicy::enabled)
                .toList();

        new RateLimitPolicyResolver(validatedPolicies);
        return validatedPolicies;
    }

    public record PolicyProperties(
            boolean enabled,
            String stateVersion,
            String routeId,
            List<HttpMethod> methods,
            String identityStrategy,
            Long capacity,
            Long refillTokens,
            Duration refillPeriod,
            Long requestCost,
            String failureMode) {

        private RateLimitPolicy toPolicy(String policyId) {
            validateMvpIdentifier(policyId);
            validateStateVersion(stateVersion);
            validateMvpSelector(policyId, routeId, methods, identityStrategy, failureMode);
            long validatedCapacity = requiredLong(capacity, "capacity");
            long validatedRefillTokens = requiredLong(refillTokens, "refill tokens");
            Duration validatedRefillPeriod = requiredDuration(refillPeriod, "refill period");
            long validatedRequestCost = requiredLong(requestCost, "request cost");
            validateApprovedNumbers(policyId, validatedCapacity, validatedRefillTokens, validatedRefillPeriod, validatedRequestCost);

            return RateLimitPolicy.create(
                    policyId,
                    stateVersion,
                    routeId,
                    Set.copyOf(methods),
                    identityStrategy,
                    validatedCapacity,
                    validatedRefillTokens,
                    validatedRefillPeriod,
                    validatedRequestCost,
                    failureMode,
                    enabled);
        }

        private static void validateApprovedNumbers(String policyId, long capacity, long refillTokens,
                                                    Duration refillPeriod, long requestCost) {
            if ("public-catalog-read".equals(policyId)) {
                return;
            }
            boolean valid = switch (policyId) {
                case "public-catalog-read" -> capacity == 60 && refillTokens == 30
                        && Duration.ofSeconds(1).equals(refillPeriod) && requestCost == 1;
                case "auth-login" -> capacity == 10 && refillTokens == 10
                        && Duration.ofMinutes(1).equals(refillPeriod) && requestCost == 1;
                case "auth-refresh" -> capacity == 30 && refillTokens == 30
                        && Duration.ofMinutes(1).equals(refillPeriod) && requestCost == 1;
                default -> false;
            };
            if (!valid) throw new IllegalArgumentException(policyId + " must use its approved MVP rate-limit preset");
        }

        private static void validateMvpIdentifier(String policyId) {
            if (!APPROVED_POLICY_IDS.contains(policyId)) {
                throw new IllegalArgumentException("unsupported rate-limit policy for Feature 015: " + policyId);
            }
        }

        private static void validateStateVersion(String stateVersion) {
            if (stateVersion == null || !STATE_VERSION_PATTERN.matcher(stateVersion).matches()) {
                throw new IllegalArgumentException("rate-limit state version must match p[1-9][0-9]{0,8}");
            }
        }

        private static void validateMvpSelector(
                String policyId, String routeId, List<HttpMethod> methods, String identityStrategy, String failureMode) {
            String expectedRoute = switch (policyId) {
                case "public-catalog-read" -> "product-catalog";
                case "auth-login" -> "authentication-login";
                case "auth-refresh" -> "authentication-refresh";
                default -> throw new IllegalArgumentException("unsupported rate-limit policy: " + policyId);
            };
            Set<HttpMethod> expectedMethods = "public-catalog-read".equals(policyId) ? Set.of(HttpMethod.GET) : Set.of(HttpMethod.POST);
            if (!expectedRoute.equals(routeId)) {
                throw new IllegalArgumentException(policyId + " rate-limit route id must be " + expectedRoute);
            }
            if (methods == null || !Set.copyOf(methods).equals(expectedMethods)) {
                throw new IllegalArgumentException(policyId + " rate-limit methods are invalid for the approved preset");
            }
            if (!"CLIENT_IP".equals(identityStrategy)) {
                throw new IllegalArgumentException("rate-limit identity strategy must be CLIENT_IP");
            }
            if (!"ALLOW_WITH_METRIC".equals(failureMode)) {
                throw new IllegalArgumentException("rate-limit failure mode must be ALLOW_WITH_METRIC");
            }
        }

        private static long requiredLong(Long value, String fieldName) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
            return value;
        }

        private static Duration requiredDuration(Duration value, String fieldName) {
            if (value == null) {
                throw new IllegalArgumentException(fieldName + " is required");
            }
            return value;
        }
    }
}
