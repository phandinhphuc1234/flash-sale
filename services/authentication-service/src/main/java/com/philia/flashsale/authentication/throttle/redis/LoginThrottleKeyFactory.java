package com.philia.flashsale.authentication.throttle.redis;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.philia.flashsale.authentication.session.application.login.LoginThrottleUnavailableException;
import com.philia.flashsale.authentication.configuration.AuthenticationProperties;

/** Creates non-reversible, bounded Redis keys so raw login identifiers never reach Redis. */
/** Creates privacy-preserving HMAC Redis keys for login throttling. */
public final class LoginThrottleKeyFactory {
    private final String secret;

    public LoginThrottleKeyFactory(AuthenticationProperties properties) {
        this.secret = properties.throttle() == null ? null : properties.throttle().hmacSecret();
    }

    public String key(String kind, String normalizedIdentifier) {
        return "auth:" + kind + ":v1:" + hmac(normalizedIdentifier);
    }

    private String hmac(String identifier) {
        try {
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException("Throttle HMAC secret is not configured");
            }
            byte[] secretBytes = Base64.getDecoder().decode(secret);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal(identifier.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new LoginThrottleUnavailableException("Throttle HMAC unavailable", exception);
        }
    }
}
