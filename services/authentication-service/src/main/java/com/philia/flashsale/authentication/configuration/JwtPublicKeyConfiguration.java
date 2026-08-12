package com.philia.flashsale.authentication.configuration;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import java.nio.charset.StandardCharsets;
import java.io.IOException;

/** Parses the deployment-provided public key and exposes only a public JWK. */
@Configuration
@EnableConfigurationProperties(JwtTrustProperties.class)
/** Loads public RSA material and exposes verification/JWKS infrastructure without private fields. */
public class JwtPublicKeyConfiguration {

    private final ResourceLoader resourceLoader;

    public JwtPublicKeyConfiguration(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Bean
    RSAKey authenticationPublicJwk(JwtTrustProperties properties) {
        requireText(properties.issuer(), "flashsale.auth.jwt.issuer");
        requireText(properties.audience(), "flashsale.auth.jwt.audience");
        requireText(properties.keyId(), "flashsale.auth.jwt.key-id");
        String publicKey = resolveKey(properties.publicKeyPem(), properties.publicKeyLocation(),
                "flashsale.auth.jwt.public-key-pem/public-key-location");

        return new RSAKey.Builder(parsePublicKey(publicKey))
                .keyID(properties.keyId())
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    private String resolveKey(String pem, String location, String property) {
        if (StringUtils.hasText(pem)) return pem;
        if (!StringUtils.hasText(location)) throw new IllegalStateException(property + " must be configured; refusing to start without a trusted key");
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) throw new IllegalStateException(property + " resource does not exist");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(property + " cannot be read", exception);
        }
    }

    private RSAPublicKey parsePublicKey(String pem) {
        try {
            String encoded = pem
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(encoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception exception) {
            throw new IllegalStateException("JWT public key must be a valid RSA PUBLIC KEY PEM", exception);
        }
    }

    private void requireText(String value, String property) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(property + " must be configured; refusing to start without a trusted key");
        }
    }
}
