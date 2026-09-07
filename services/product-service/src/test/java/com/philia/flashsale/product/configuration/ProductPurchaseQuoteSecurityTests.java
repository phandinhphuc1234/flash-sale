package com.philia.flashsale.product.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Keeps Order's purchase-quote capability isolated from Cart and public JWT permissions. */
class ProductPurchaseQuoteSecurityTests {

    private static final ProductOrderInternalJwtProperties PROPERTIES = new ProductOrderInternalJwtProperties(
            "https://auth.example.test", "https://auth.example.test/jwks", "flash-sale-internal-api",
            "order-service", "catalog.purchase-quote.read");

    @Test
    void rejectsMissingWrongSubjectAndWrongScope() {
        assertThat(ProductPurchaseQuoteSecurityConfiguration.authorize(() -> null, PROPERTIES).isGranted())
                .isFalse();
        assertThat(ProductPurchaseQuoteSecurityConfiguration.authorize(
                () -> authentication("cart-service", "catalog.purchase-quote.read"), PROPERTIES).isGranted())
                .isFalse();
        assertThat(ProductPurchaseQuoteSecurityConfiguration.authorize(
                () -> authentication("order-service", "catalog.variant-display.read"), PROPERTIES).isGranted())
                .isFalse();
    }

    @Test
    void acceptsOrderOnlyWithPurchaseQuoteScope() {
        assertThat(ProductPurchaseQuoteSecurityConfiguration.authorize(
                () -> authentication("order-service", "catalog.purchase-quote.read"), PROPERTIES).isGranted())
                .isTrue();
    }

    private static JwtAuthenticationToken authentication(String subject, String scope) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test")
                .header("alg", "RS256")
                .header("typ", "at+jwt")
                .issuer("https://auth.example.test")
                .subject(subject)
                .audience(List.of("flash-sale-internal-api"))
                .claim("scope", scope)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("SCOPE_" + scope)));
    }
}
