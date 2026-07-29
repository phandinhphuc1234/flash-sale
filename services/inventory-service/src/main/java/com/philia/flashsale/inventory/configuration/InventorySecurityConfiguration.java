package com.philia.flashsale.inventory.configuration;

import java.util.ArrayList;
import java.util.Collection;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InventorySecurityConfiguration {
    @Bean
    SecurityFilterChain inventorySecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                .requestMatchers("/api/v1/admin/inventory/**").hasAuthority("INVENTORY_ADMIN")
                .requestMatchers("/internal/v1/**").hasAuthority("SCOPE_INVENTORY_WRITE")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(inventoryJwtConverter())));
        return http.build();
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> inventoryJwtConverter() {
        return jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            Object values = jwt.getClaims().get("authorities");
            if (values instanceof Collection<?> collection) collection.forEach(value -> authorities.add(new SimpleGrantedAuthority(String.valueOf(value))));
            Object roles = jwt.getClaims().get("roles");
            if (roles instanceof Collection<?> collection) collection.forEach(value -> authorities.add(new SimpleGrantedAuthority(String.valueOf(value))));
            String scope = jwt.getClaimAsString("scope");
            if (scope != null) for (String value : scope.split(" ")) authorities.add(new SimpleGrantedAuthority("SCOPE_" + value));
            return new JwtAuthenticationToken(jwt, authorities);
        };
    }
}
