package com.philia.flashsale.inventory.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.http.HttpMethod;

/** Narrows only Campaign allocation to the dedicated internal service identity and scope. */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InventoryInternalSecurityConfiguration {
    @Bean
    @Order(2)
    SecurityFilterChain inventoryCampaignAllocationSecurity(HttpSecurity http,
            @Qualifier("inventoryInternalJwtDecoder") JwtDecoder decoder,
            InventoryInternalJwtProperties properties) throws Exception {
        return http.securityMatcher(new AntPathRequestMatcher(
                        "/internal/v1/campaign-stock-allocations", HttpMethod.POST.name()))
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest()
                        .access((authentication, context) -> authorize(authentication, properties)))
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.decoder(decoder)
                        .jwtAuthenticationConverter(internalJwtConverter(properties))))
                .build();
    }

    private AuthorizationDecision authorize(Supplier<Authentication> supplier,
            InventoryInternalJwtProperties properties) {
        Authentication authentication = supplier.get();
        boolean subjectMatches = authentication.getPrincipal() instanceof Jwt jwt
                && properties.subject().equals(jwt.getSubject());
        boolean scopeMatches = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(("SCOPE_" + properties.requiredScope())::equals);
        return new AuthorizationDecision(subjectMatches && scopeMatches);
    }

    private JwtAuthenticationConverter internalJwtConverter(InventoryInternalJwtProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            if (!properties.subject().equals(jwt.getSubject())) return List.of();
            List<GrantedAuthority> authorities = new ArrayList<>();
            String scopes = jwt.getClaimAsString("scope");
            if (scopes != null) {
                for (String scope : scopes.split(" ")) {
                    if (!scope.isBlank()) authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope.trim()));
                }
            }
            return authorities;
        });
        return converter;
    }
}
