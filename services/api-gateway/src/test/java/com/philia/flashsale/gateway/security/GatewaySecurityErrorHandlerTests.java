package com.philia.flashsale.gateway.security;

import static com.philia.flashsale.gateway.error.GatewayErrorCode.ACCESS_DENIED;
import static com.philia.flashsale.gateway.error.GatewayErrorCode.AUTHENTICATION_UNAVAILABLE;
import static com.philia.flashsale.gateway.error.GatewayErrorCode.CATALOG_ADMIN_REQUIRED;
import static com.philia.flashsale.gateway.error.GatewayErrorCode.UNAUTHENTICATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.philia.flashsale.gateway.error.GatewayFailureClassifier;
import com.philia.flashsale.gateway.error.GatewayHttpErrorWriter;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

class GatewaySecurityErrorHandlerTests {

    private GatewayHttpErrorWriter errorWriter;
    private GatewaySecurityErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        errorWriter = mock(GatewayHttpErrorWriter.class);
        when(errorWriter.write(any(), any(), any())).thenReturn(Mono.empty());
        errorHandler = new GatewaySecurityErrorHandler(errorWriter, new GatewayFailureClassifier());
    }

    @ParameterizedTest
    @MethodSource("ordinaryAuthenticationFailures")
    void invalidCredentialsRemainUnauthenticated(AuthenticationException failure) {
        MockServerWebExchange exchange = exchange("/api/v1/admin/catalog/products");

        errorHandler.commence(exchange, failure).block();

        assertThat(exchange.getResponse().getHeaders().get(HttpHeaders.WWW_AUTHENTICATE))
                .containsExactly("Bearer");
        verify(errorWriter).write(exchange, UNAUTHENTICATED, failure);
    }

    @ParameterizedTest
    @MethodSource("authenticationInfrastructureCauses")
    void approvedAuthenticationInfrastructureCausesReturnServiceUnavailable(Throwable cause) {
        MockServerWebExchange exchange = exchange("/api/v1/admin/catalog/products");
        AuthenticationException failure = new InvalidBearerTokenException("verification failed", cause);

        errorHandler.commence(exchange, failure).block();

        assertThat(exchange.getResponse().getHeaders()).doesNotContainKey(HttpHeaders.WWW_AUTHENTICATE);
        verify(errorWriter).write(exchange, AUTHENTICATION_UNAVAILABLE, failure);
    }

    @Test
    void productAdminAccessDenialUsesRouteSpecificCode() {
        MockServerWebExchange exchange = exchange("/api/v1/admin/catalog/products");
        AccessDeniedException failure = new AccessDeniedException("missing authority");

        errorHandler.handle(exchange, failure).block();

        verify(errorWriter).write(exchange, CATALOG_ADMIN_REQUIRED, failure);
    }

    @Test
    void otherAccessDenialUsesGenericCode() {
        MockServerWebExchange exchange = exchange("/not-a-configured-route");
        AccessDeniedException failure = new AccessDeniedException("denied by default");

        errorHandler.handle(exchange, failure).block();

        verify(errorWriter).write(exchange, ACCESS_DENIED, failure);
    }

    private static Stream<AuthenticationException> ordinaryAuthenticationFailures() {
        return Stream.of(
                new BadCredentialsException("missing credentials"),
                new InvalidBearerTokenException("malformed token"),
                new InvalidBearerTokenException("bad JWT", new BadJwtException("bad signature")));
    }

    private static Stream<Throwable> authenticationInfrastructureCauses() {
        return Stream.of(
                new AuthenticationServiceException("authentication prerequisite failed"),
                new JwtDecoderInitializationException(
                        "decoder initialization failed", new IllegalStateException("initialization")),
                new WebClientRequestException(
                        new ConnectException("connection refused"),
                        HttpMethod.GET,
                        URI.create("http://authentication-service/.well-known/jwks.json"),
                        HttpHeaders.EMPTY),
                WebClientResponseException.create(
                        503,
                        "Service Unavailable",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8),
                new ConnectException("connection refused"),
                new UnknownHostException("authentication-service"),
                new SocketTimeoutException("verification timeout"),
                new UnresolvedAddressException(),
                new ConnectTimeoutException("connect timeout"),
                ReadTimeoutException.INSTANCE,
                PrematureCloseException.TEST_EXCEPTION,
                remoteKeySourceException());
    }

    private static Throwable remoteKeySourceException() {
        try {
            Class<?> type = Class.forName("com.nimbusds.jose.RemoteKeySourceException");
            return (Throwable) type
                    .getConstructor(String.class, Throwable.class)
                    .newInstance("remote key source unavailable", new IllegalStateException("provider"));
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InstantiationException
                | IllegalAccessException
                | InvocationTargetException exception) {
            throw new AssertionError("Nimbus RemoteKeySourceException must be available at runtime", exception);
        }
    }

    private static MockServerWebExchange exchange(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).build());
    }
}
