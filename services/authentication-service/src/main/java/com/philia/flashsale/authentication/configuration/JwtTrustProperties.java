package com.philia.flashsale.authentication.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime values that define the public JWT trust contract. */
@ConfigurationProperties(prefix = "flashsale.auth.jwt")
/** Typed issuer/audience/key material settings shared by JWKS and signing configuration. */
public record JwtTrustProperties(
        String issuer,
        String audience,
        String keyId,
        String publicKeyPem,
        String publicKeyLocation,
        String privateKeyLocation,
        String privateKeyPem) {
}
