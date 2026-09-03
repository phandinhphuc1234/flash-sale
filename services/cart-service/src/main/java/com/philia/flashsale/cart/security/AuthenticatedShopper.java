package com.philia.flashsale.cart.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Typed owner identity derived only from the validated public shopper JWT. */
public record AuthenticatedShopper(UUID subject) {

    public static AuthenticatedShopper from(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InvalidCartPrincipalException("Authenticated shopper JWT is required");
        }
        Jwt jwt = authentication instanceof JwtAuthenticationToken jwtAuthentication
                ? jwtAuthentication.getToken()
                : authentication.getPrincipal() instanceof Jwt principalJwt ? principalJwt : null;
        if (jwt == null) {
            throw new InvalidCartPrincipalException("Authenticated shopper JWT is required");
        }
        String rawSubject = jwt.getSubject();
        if (rawSubject == null || rawSubject.isBlank()) {
            throw new InvalidCartPrincipalException("Authenticated shopper subject is required");
        }
        try {
            return new AuthenticatedShopper(UUID.fromString(rawSubject));
        } catch (IllegalArgumentException exception) {
            throw new InvalidCartPrincipalException("Authenticated shopper subject must be a UUID");
        }
    }
}
