package com.philia.flashsale.authentication.security.password;

import com.philia.flashsale.authentication.session.application.login.PasswordProofPort;
import com.philia.flashsale.authentication.account.application.registration.EncodePasswordPort;
import com.philia.flashsale.authentication.configuration.AuthenticationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/** Provider adapter for the approved Argon2id password proof policy. */
@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "core-enabled",
        havingValue = "true", matchIfMissing = true)
/** Spring Security Argon2 adapter behind framework-free password capabilities. */
public class Argon2PasswordProofAdapter implements EncodePasswordPort, PasswordProofPort {
    private static final String DUMMY_PASSWORD = "authentication-dummy-password-proof";

    private final Argon2PasswordEncoder encoder;
    private final String dummyHash;

    public Argon2PasswordProofAdapter(AuthenticationProperties properties) {
        AuthenticationProperties.Argon2 policy = properties.argon2();
        this.encoder = new Argon2PasswordEncoder(
                policy.saltLength(), policy.hashLength(), policy.parallelism(),
                policy.memoryKib(), policy.iterations());
        // Generated once at startup so unknown accounts pay the same configured verification cost.
        this.dummyHash = encoder.encode(DUMMY_PASSWORD);
    }

    @Override
    public String encode(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public void verifyDummy(String rawPassword) {
        encoder.matches(rawPassword, dummyHash);
    }
}
