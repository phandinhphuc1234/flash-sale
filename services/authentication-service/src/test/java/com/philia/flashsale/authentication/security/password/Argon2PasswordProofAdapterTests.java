package com.philia.flashsale.authentication.security.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import org.junit.jupiter.api.Test;

class Argon2PasswordProofAdapterTests {

    @Test
    void encodedPasswordCarriesTheConfiguredArgon2idCost() {
        Argon2PasswordProofAdapter adapter = new Argon2PasswordProofAdapter(properties());

        String encoded = adapter.encode("a sufficiently long password");

        assertThat(encoded).startsWith("$argon2id$").contains("m=19456,t=2,p=1");
        assertThat(adapter.matches("a sufficiently long password", encoded)).isTrue();
    }

    @Test
    void dummyVerificationUsesTheSameConfiguredEncoder() {
        Argon2PasswordProofAdapter adapter = new Argon2PasswordProofAdapter(properties());

        adapter.verifyDummy("unknown-account-password");
    }

    private AuthenticationProperties properties() {
        return new AuthenticationProperties(
                new AuthenticationProperties.Cookie(false, "Lax", "/api/v1/auth"),
                "https://app.example",
                new AuthenticationProperties.Throttle("secret", Duration.ofMinutes(15), Duration.ofMinutes(15), 5),
                new AuthenticationProperties.Retention(30),
                "0 0 3 * * *",
                new AuthenticationProperties.Argon2(16, 32, 1, 19_456, 2));
    }
}
