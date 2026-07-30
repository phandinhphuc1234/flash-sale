package com.philia.flashsale.authentication.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;

class JwtSigningConfigurationTests {

    private static final String ISSUER = "https://auth.example.test";
    private static final String AUDIENCE = "flash-sale-api";
    private static final String KEY_ID = "test-key-1";

    private static RSAKey publicJwk;
    private static RSAPrivateKey privateKey;

    @BeforeAll
    static void generateSigningKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
        privateKey = (RSAPrivateKey) pair.getPrivate();
        publicJwk = new RSAKey.Builder(publicKey)
                .keyID(KEY_ID)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    @Test
    void decoderAcceptsTheConfiguredAudience() {
        TestJwtInfrastructure infrastructure = infrastructure();

        String token = encode(infrastructure.encoder(), List.of(AUDIENCE));

        assertThatCode(() -> infrastructure.decoder().decode(token)).doesNotThrowAnyException();
    }

    @Test
    void decoderRejectsAMissingAudience() {
        TestJwtInfrastructure infrastructure = infrastructure();

        String token = encode(infrastructure.encoder(), null);

        assertThatThrownBy(() -> infrastructure.decoder().decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void decoderRejectsAnUnexpectedAudience() {
        TestJwtInfrastructure infrastructure = infrastructure();

        String token = encode(infrastructure.encoder(), List.of("another-api"));

        assertThatThrownBy(() -> infrastructure.decoder().decode(token))
                .isInstanceOf(JwtValidationException.class);
    }

    private TestJwtInfrastructure infrastructure() {
        JwtSigningConfiguration configuration = new JwtSigningConfiguration(new DefaultResourceLoader());
        JwtTrustProperties properties = new JwtTrustProperties(
                ISSUER, AUDIENCE, KEY_ID, null, null, null, null);
        JwtEncoder encoder = configuration.authenticationJwtEncoder(publicJwk, privateKey, properties);
        return new TestJwtInfrastructure(encoder, configuration.authenticationJwtDecoder(publicJwk, properties));
    }

    private String encode(JwtEncoder encoder, List<String> audiences) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .id(UUID.randomUUID().toString());
        if (audiences != null) {
            claims.audience(audiences);
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("at+jwt")
                .keyId(KEY_ID)
                .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    private record TestJwtInfrastructure(
            JwtEncoder encoder,
            org.springframework.security.oauth2.jwt.JwtDecoder decoder) {
    }
}
