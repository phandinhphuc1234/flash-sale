package com.philia.flashsale.authentication.configuration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.philia.flashsale.authentication.serviceclient.adapter.in.oauth.DurableRegisteredClientRepository;
import com.philia.flashsale.authentication.serviceclient.application.AuthenticateServiceClientUseCase;
import com.philia.flashsale.authentication.serviceclient.application.LoadServiceClientPort;
import com.philia.flashsale.authentication.serviceclient.application.ServiceClientAuthenticationService;
import com.philia.flashsale.authentication.serviceclient.application.ServiceClientSecretVerifierPort;
import com.philia.flashsale.authentication.serviceclient.application.ProvisionServiceClientPort;
import com.philia.flashsale.authentication.serviceclient.application.ProvisionServiceClientService;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

/** Composes the internal client-credentials endpoint without changing public-user JWT issuance. */
@Configuration
@org.springframework.boot.context.properties.EnableConfigurationProperties({ServiceTokenProperties.class, ServiceClientsProperties.class})
@ConditionalOnProperty(prefix = "flashsale.authentication", name = "runtime-enabled", havingValue = "true")
public class ServiceClientAuthorizationConfiguration {

    @Bean
    PasswordEncoder serviceClientPasswordEncoder(AuthenticationProperties properties) {
        AuthenticationProperties.Argon2 policy = properties.argon2();
        return new Argon2PasswordEncoder(policy.saltLength(), policy.hashLength(), policy.parallelism(), policy.memoryKib(), policy.iterations());
    }

    @Bean
    AuthenticateServiceClientUseCase authenticateServiceClientUseCase(LoadServiceClientPort clients,
            ServiceClientSecretVerifierPort secrets) {
        return new ServiceClientAuthenticationService(clients, secrets);
    }

    @Bean
    ProvisionServiceClientService provisionServiceClientService(ProvisionServiceClientPort provisioner) {
        return new ProvisionServiceClientService(provisioner);
    }

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        return http.build();
    }

    @Bean
    JWKSource<SecurityContext> authorizationJwkSource(RSAKey publicJwk, RSAPrivateKey privateKey,
            JwtTrustProperties properties) {
        try {
            RSAKey signingKey = new RSAKey.Builder((java.security.interfaces.RSAPublicKey) publicJwk.toRSAPublicKey())
                    .privateKey(privateKey).keyID(properties.keyId()).algorithm(JWSAlgorithm.RS256).build();
            return new ImmutableJWKSet<>(new JWKSet(signingKey));
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new IllegalStateException("OAuth signing key cannot be constructed", exception);
        }
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(JwtTrustProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> serviceTokenCustomizer(JwtTrustProperties properties,
            ServiceTokenProperties serviceTokenProperties) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                    && context.getRegisteredClient().getAuthorizationGrantTypes().contains(
                            org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)) {
                context.getJwsHeader().type("at+jwt");
                context.getClaims().issuer(properties.issuer());
                context.getClaims().subject(context.getRegisteredClient().getClientId());
                context.getClaims().audience(java.util.List.of(serviceTokenProperties.audience()));
                context.getClaims().claim("scope", String.join(" ", context.getAuthorizedScopes()));
            }
        };
    }
}
