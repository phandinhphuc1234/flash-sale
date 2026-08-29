package com.philia.flashsale.cart.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticatedShopperTests {

    @Test
    void derivesOnlyUuidSubjectFromValidatedJwt() {
        UUID subject = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject.toString()).build();

        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of());
        authentication.setAuthenticated(true);
        assertThat(AuthenticatedShopper.from(authentication).subject())
                .isEqualTo(subject);
    }

    @Test
    void rejectsNonUuidOrNonJwtIdentities() {
        Jwt nonUuid = Jwt.withTokenValue("token").header("alg", "none").subject("shopper-1").build();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(nonUuid, List.of());
        authentication.setAuthenticated(true);
        assertThatThrownBy(() -> AuthenticatedShopper.from(authentication))
                .isInstanceOf(InvalidCartPrincipalException.class);
        assertThatThrownBy(() -> AuthenticatedShopper.from(null))
                .isInstanceOf(InvalidCartPrincipalException.class);
    }
}
