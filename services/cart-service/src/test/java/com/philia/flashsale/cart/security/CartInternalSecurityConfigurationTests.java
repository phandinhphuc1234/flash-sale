package com.philia.flashsale.cart.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Proves the future snapshot route accepts only Order's exact internal capability. */
class CartInternalSecurityConfigurationTests {

    private static final CartInternalJwtProperties PROPERTIES = new CartInternalJwtProperties(
            "https://auth.example.test", "https://auth.example.test/jwks", "flash-sale-internal-api",
            "order-service", "cart.checkout-snapshot.read");

    @Test
    void deniesMissingOrWrongMachineCapability() {
        assertThat(CartInternalSecurityConfiguration.authorize(() -> null, PROPERTIES).isGranted()).isFalse();
        assertThat(CartInternalSecurityConfiguration.authorize(
                () -> authentication("cart-service", "cart.checkout-snapshot.read"), PROPERTIES).isGranted())
                .isFalse();
        assertThat(CartInternalSecurityConfiguration.authorize(
                () -> authentication("order-service", "catalog.purchase-quote.read"), PROPERTIES).isGranted())
                .isFalse();
    }

    @Test
    void acceptsOnlyOrderWithTheSnapshotScope() {
        assertThat(CartInternalSecurityConfiguration.authorize(
                () -> authentication("order-service", "cart.checkout-snapshot.read"), PROPERTIES).isGranted())
                .isTrue();
    }

    private static JwtAuthenticationToken authentication(String subject, String scope) {
        return new JwtAuthenticationToken(jwt(subject, scope),
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }

    private static Jwt jwt(String subject, String scope) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .issuer("https://auth.example.test")
                .subject(subject)
                .audience(List.of("flash-sale-internal-api"))
                .claim("scope", scope)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
    }
}
