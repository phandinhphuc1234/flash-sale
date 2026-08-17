package com.philia.flashsale.authentication.configuration;

import com.nimbusds.jose.JOSEException;
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
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.ClientSecretAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;

/** Composes the internal client-credentials endpoint without changing public-user JWT issuance. */
@Configuration
@EnableConfigurationProperties({ServiceTokenProperties.class, ServiceClientsProperties.class})
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
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @Conditional(JwtPrivateKeyConfiguredCondition.class)
    SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http, PasswordEncoder passwordEncoder)
            throws Exception {
        http.objectPostProcessor(new ObjectPostProcessor<Object>() {
            @Override
            public <O> O postProcess(O object) {
                if (object instanceof ClientSecretAuthenticationProvider provider) {
                    provider.setPasswordEncoder(passwordEncoder);
                }
                return object;
            }
        });
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        OAuth2AuthorizationServerConfigurer authorizationServer =
                http.getConfigurer(OAuth2AuthorizationServerConfigurer.class);
        authorizationServer.clientAuthentication(clientAuthentication ->
                clientAuthentication.authenticationProviders(providers -> {
                    providers.removeIf(ClientSecretAuthenticationProvider.class::isInstance);
                    RegisteredClientRepository clients = http.getSharedObject(RegisteredClientRepository.class);
                    OAuth2AuthorizationService authorizationService =
                            http.getSharedObject(OAuth2AuthorizationService.class);
                    ClientSecretAuthenticationProvider provider =
                            new ClientSecretAuthenticationProvider(clients, authorizationService);
                    provider.setPasswordEncoder(passwordEncoder);
                    providers.add(provider);
                }));
        return http.build();
    }

    @Bean
    @Conditional(JwtPrivateKeyConfiguredCondition.class)
    JWKSource<SecurityContext> authorizationJwkSource(RSAKey publicJwk, RSAPrivateKey privateKey,
            JwtTrustProperties properties) {
        try {
            RSAKey signingKey = new RSAKey.Builder((RSAPublicKey) publicJwk.toRSAPublicKey())
                    .privateKey(privateKey).keyID(properties.keyId()).algorithm(JWSAlgorithm.RS256).build();
            return new ImmutableJWKSet<>(new JWKSet(signingKey));
        } catch (JOSEException exception) {
            throw new IllegalStateException("OAuth signing key cannot be constructed", exception);
        }
    }

    @Bean
    @Conditional(JwtPrivateKeyConfiguredCondition.class)
    AuthorizationServerSettings authorizationServerSettings(JwtTrustProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }

    @Bean
    @Conditional(JwtPrivateKeyConfiguredCondition.class)
    OAuth2TokenCustomizer<JwtEncodingContext> serviceTokenCustomizer(JwtTrustProperties properties,
            ServiceTokenProperties serviceTokenProperties) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                    && context.getRegisteredClient().getAuthorizationGrantTypes().contains(
                            AuthorizationGrantType.CLIENT_CREDENTIALS)) {
                context.getJwsHeader().type("at+jwt");
                context.getClaims().issuer(properties.issuer());
                context.getClaims().subject(context.getRegisteredClient().getClientId());
                context.getClaims().audience(List.of(serviceTokenProperties.audience()));
                context.getClaims().claim("scope", String.join(" ", context.getAuthorizedScopes()));
            }
        };
    }
}
