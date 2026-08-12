package com.philia.flashsale.authentication.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class AuthenticationPropertiesTests {

    @Test
    void rejectsArgon2CostsBelowTheApprovedSecurityFloor() {
        AuthenticationProperties properties = properties(
                new AuthenticationProperties.Argon2(15, 31, 0, 19_455, 1));

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Set<ConstraintViolation<AuthenticationProperties>> violations = factory.getValidator().validate(properties);

            assertThat(violations)
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains("argon2.saltLength", "argon2.hashLength", "argon2.parallelism",
                            "argon2.memoryKib", "argon2.iterations");
        }
    }

    @Test
    void invalidArgon2BindingFailsApplicationContextStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesConfiguration.class)
                .withPropertyValues(
                        "flashsale.authentication.argon2.salt-length=15",
                        "flashsale.authentication.argon2.hash-length=31",
                        "flashsale.authentication.argon2.parallelism=0",
                        "flashsale.authentication.argon2.memory-kib=19455",
                        "flashsale.authentication.argon2.iterations=1")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCause(context.getStartupFailure()))
                            .hasMessageContaining("argon2.iterations")
                            .hasMessageContaining("argon2.memoryKib");
                });
    }

    private AuthenticationProperties properties(AuthenticationProperties.Argon2 argon2) {
        return new AuthenticationProperties(
                new AuthenticationProperties.Cookie(false, "Lax", "/api/v1/auth"),
                "https://app.example",
                new AuthenticationProperties.Throttle("secret", Duration.ofMinutes(15), Duration.ofMinutes(15), 5),
                new AuthenticationProperties.Retention(30),
                "0 0 3 * * *",
                argon2);
    }

    private Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AuthenticationProperties.class)
    static class PropertiesConfiguration { }
}
