package com.philia.flashsale.authentication.configuration;

import java.time.Duration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Typed runtime policy grouped by authentication capability. */
@Validated
@ConfigurationProperties(prefix = "flashsale.authentication")
public record AuthenticationProperties(
        Cookie cookie,
        String trustedOrigins,
        Throttle throttle,
        Retention retention,
        String cleanupCron,
        @Valid @NotNull Argon2 argon2) {
    public record Cookie(boolean secure, String sameSite, String path) { }
    public record Throttle(String hmacSecret, Duration failureWindow, Duration cooldown, int maxFailures) { }
    public record Retention(int days) { }
    public record Argon2(
            @Min(16) int saltLength,
            @Min(32) int hashLength,
            @Min(1) int parallelism,
            @Min(19_456) int memoryKib,
            @Min(2) int iterations) { }
}
