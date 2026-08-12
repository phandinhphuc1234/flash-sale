package com.philia.flashsale.authentication.serviceclient.adapter.in.oauth;

import com.philia.flashsale.authentication.serviceclient.application.ServiceClientSecretVerifierPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true")
public class ServiceClientPasswordVerifierAdapter implements ServiceClientSecretVerifierPort {
    private final PasswordEncoder encoder;
    public ServiceClientPasswordVerifierAdapter(PasswordEncoder encoder) { this.encoder = encoder; }
    @Override public boolean matches(String rawSecret, String encodedSecret) { return encoder.matches(rawSecret, encodedSecret); }
}
