package com.philia.flashsale.authentication.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.context.annotation.Conditional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(JwtTrustProperties.class)
@Conditional(JwtPrivateKeyConfiguredCondition.class)
/** Loads PKCS#8 private RSA material and creates the local RS256 encoder/decoder. */
public class JwtSigningConfiguration {
    private final ResourceLoader resourceLoader;

    public JwtSigningConfiguration(ResourceLoader resourceLoader) { this.resourceLoader = resourceLoader; }

    @Bean
    RSAPrivateKey authenticationPrivateKey(JwtTrustProperties properties) {
        String pem = read(properties.privateKeyPem(), properties.privateKeyLocation());
        return parsePrivateKey(pem);
    }

    @Bean
    JwtEncoder authenticationJwtEncoder(RSAKey publicJwk, RSAPrivateKey privateKey, JwtTrustProperties properties) {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) publicJwk.toRSAPublicKey();
            if (!publicKey.getModulus().equals(privateKey.getModulus())) {
                throw new IllegalStateException("JWT public and private keys must use the same RSA modulus");
            }
            RSAKey signingKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey).keyID(properties.keyId()).build();
            return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey)));
        } catch (JOSEException exception) {
            throw new IllegalStateException("JWT signing key cannot be constructed", exception);
        }
    }

    @Bean
    JwtDecoder authenticationJwtDecoder(RSAKey publicJwk, JwtTrustProperties properties) {
        try {
            // RFC 9068 access tokens use typ=at+jwt; keep the check explicit rather than
            // relying on Spring's default typ=JWT validator.
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(
                    (RSAPublicKey) publicJwk.toRSAPublicKey())
                    .validateType(false)
                    .build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(properties.issuer()),
                    token -> token.getAudience() != null && token.getAudience().contains(properties.audience())
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                    "invalid_token", "Required JWT audience is missing", null)),
                    token -> "at+jwt".equals(token.getHeaders().get("typ"))
                            ? OAuth2TokenValidatorResult.success()
                            : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                    "invalid_token", "Required JWT type is missing", null))));
            return decoder;
        } catch (JOSEException exception) {
            throw new IllegalStateException("JWT public key cannot be constructed", exception);
        }
    }

    private String read(String pem, String location) {
        if (StringUtils.hasText(pem)) return pem;
        if (!StringUtils.hasText(location)) throw new IllegalStateException("JWT private key location must be configured");
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) throw new IllegalStateException("JWT private key resource does not exist");
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("JWT private key cannot be read", exception);
        }
    }

    private RSAPrivateKey parsePrivateKey(String pem) {
        try {
            String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(encoded);
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            if (!(key instanceof RSAPrivateKey rsa) || rsa.getModulus().bitLength() < 2048) {
                throw new IllegalStateException("JWT private key must be RSA with at least 2048 bits");
            }
            return rsa;
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("JWT private key must be PKCS#8 RSA PEM", exception);
        }
    }
}
