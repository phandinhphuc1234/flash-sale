package com.philia.flashsale.inventory.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Ensures regular-hold access cannot broaden the pre-existing Campaign allocation boundary. */
class InventoryRegularHoldSecurityConfigurationTests {

    private static final InventoryRegularHoldJwtProperties PROPERTIES = new InventoryRegularHoldJwtProperties(
            "https://auth.example.test", "https://auth.example.test/jwks", "flash-sale-internal-api",
            "order-service", "inventory.regular-hold.write");

    @Test
    void rejectsMissingCampaignAndWrongScopeTokens() {
        assertThat(InventoryRegularHoldSecurityConfiguration.authorize(() -> null, PROPERTIES).isGranted())
                .isFalse();
        assertThat(InventoryRegularHoldSecurityConfiguration.authorize(
                () -> authentication("campaign-service", "inventory.regular-hold.write"), PROPERTIES).isGranted())
                .isFalse();
        assertThat(InventoryRegularHoldSecurityConfiguration.authorize(
                () -> authentication("order-service", "inventory.campaign.allocate"), PROPERTIES).isGranted())
                .isFalse();
    }

    @Test
    void acceptsOrderOnlyWithRegularHoldScope() {
        assertThat(InventoryRegularHoldSecurityConfiguration.authorize(
                () -> authentication("order-service", "inventory.regular-hold.write"), PROPERTIES).isGranted())
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
