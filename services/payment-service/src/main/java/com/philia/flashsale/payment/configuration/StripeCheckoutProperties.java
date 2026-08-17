package com.philia.flashsale.payment.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Validated Stripe boundary inputs; secret values are never logged or exposed by the service. */
@Validated
@ConfigurationProperties(prefix = "payment.stripe")
public record StripeCheckoutProperties(
        boolean enabled,
        @NotBlank @Pattern(regexp = "test|live") String mode,
        @NotNull URI apiBaseUrl,
        String secretKey,
        String publishableKey,
        String webhookSecret,
        @NotBlank String expectedApiVersion,
        @NotNull Duration webhookTolerance,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @Min(0) @Max(2) int maxNetworkRetries,
        @NotNull URI successUrl,
        @NotNull URI cancelUrl) {

    @AssertTrue(message = "Stripe credentials are required when Stripe Checkout is enabled")
    public boolean hasCredentialsWhenEnabled() {
        return !enabled || (hasText(secretKey) && hasText(publishableKey) && hasText(webhookSecret));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
