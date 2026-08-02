package com.philia.flashsale.authentication.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.philia.flashsale.authentication.account.domain.Account;
import com.philia.flashsale.authentication.account.domain.AccountRole;
import com.philia.flashsale.authentication.account.domain.AccountStatus;
import com.philia.flashsale.authentication.configuration.JwtTrustProperties;
import com.philia.flashsale.authentication.security.token.JwtAccessTokenAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Proves an Auth-issued administrator token carries the Feature 014 Gateway/Product contract. */
class JwtTrustCompatibilityIntegrationTests {

    @Test
    void issuedAdminTokenContainsCanonicalTrustAndAuthorityClaims() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                .keyID("test-key")
                .build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(
                new ImmutableJWKSet<SecurityContext>(new JWKSet(signingKey)));
        JwtAccessTokenAdapter tokenAdapter = new JwtAccessTokenAdapter(encoder,
                new JwtTrustProperties("http://authentication-service:8080", "flash-sale-api", "test-key",
                        null, null, null, null));

        Instant now = Instant.now();
        Account admin = Account.restore(UUID.randomUUID(), "admin@example.test", "admin@example.test", "admin",
                "admin", "argon-hash", AccountRole.ROLE_ADMIN, AccountStatus.ACTIVE, null, null, now, now);
        String token = tokenAdapter.issueAccessToken(admin, UUID.randomUUID(), now);

        JwtDecoder decoder = NimbusJwtDecoder.withPublicKey((java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                .validateType(false)
                .build();
        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getHeaders()).containsEntry("alg", "RS256")
                .containsEntry("kid", "test-key")
                .containsEntry("typ", "at+jwt");
        assertThat(jwt.getIssuer()).hasToString("http://authentication-service:8080");
        assertThat(jwt.getAudience()).containsExactly("flash-sale-api");
        assertThat(jwt.getSubject()).isEqualTo(admin.id().toString());
        assertThat(jwt.getClaimAsStringList("authorities"))
                .containsExactly("ROLE_ADMIN", "CATALOG_ADMIN", "INVENTORY_ADMIN", "CAMPAIGN_ADMIN");
    }
}
