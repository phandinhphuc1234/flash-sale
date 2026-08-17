package com.philia.flashsale.payment.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Service-owned transport and security boundary settings for Payment. */
@Validated
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_-]*") String provider,
        @NotBlank String publicApiPrefix,
        @NotBlank String webhookPath,
        @NotBlank String jwtIssuer,
        @NotBlank String jwtJwkSetUri,
        @NotBlank String jwtAudience,
        @NotBlank String jwtType) {
}
