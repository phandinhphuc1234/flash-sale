package com.philia.flashsale.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.nimbusds.jose.RemoteKeySourceException;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.netty.channel.AbortedException;
import reactor.netty.Connection;
import reactor.netty.http.client.PrematureCloseException;
import reactor.netty.http.client.HttpClientResponse;

class GatewayFailureClassifierTests {

    private final GatewayFailureClassifier classifier = new GatewayFailureClassifier();

    @ParameterizedTest
    @MethodSource("routedTransportFailures")
    void classifiesOnlyApprovedRoutedNoResponseTransportFailuresAsDownstreamUnavailable(
            Throwable failure) {
        assertThat(classifier.classify(routedExchange(), nested(failure)))
                .contains(GatewayErrorCode.DOWNSTREAM_UNAVAILABLE);
    }

    @ParameterizedTest
    @MethodSource("excludedRoutedFailures")
    void leavesExcludedRoutedFailuresToTheSafeCatchAll(Throwable failure) {
        assertThat(classifier.classify(routedExchange(), nested(failure)))
                .contains(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
    }

    @Test
    void requiresASelectedHttpRouteWithNoDownstreamResponse() {
        MockServerWebExchange noRoute = exchange();
        MockServerWebExchange nonHttpRoute = routedExchange();
        nonHttpRoute.getAttributes().put(GATEWAY_REQUEST_URL_ATTR, URI.create("ws://service.local"));
        MockServerWebExchange responseObtained = routedExchange();
        responseObtained.getAttributes().put(
                CLIENT_RESPONSE_ATTR, mock(HttpClientResponse.class));
        MockServerWebExchange connectionObtained = routedExchange();
        connectionObtained.getAttributes().put(
                CLIENT_RESPONSE_CONN_ATTR, mock(Connection.class));

        assertThat(classifier.classify(noRoute, new ConnectException("refused")))
                .contains(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
        assertThat(classifier.classify(nonHttpRoute, new ConnectException("refused")))
                .contains(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
        assertThat(classifier.classify(responseObtained, new ConnectException("refused")))
                .contains(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
        assertThat(classifier.classify(connectionObtained, new ConnectException("refused")))
                .contains(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
    }

    @ParameterizedTest
    @MethodSource("authenticationInfrastructureFailures")
    void classifiesTheExactAuthenticationContextAllowList(Throwable infrastructureFailure) {
        Throwable authenticationFailure = new OAuth2AuthenticationException(
                new OAuth2Error("invalid_token"), infrastructureFailure);

        assertThat(classifier.classifyAuthentication(authenticationFailure))
                .isEqualTo(GatewayErrorCode.AUTHENTICATION_UNAVAILABLE);
    }

    @ParameterizedTest
    @MethodSource("ordinaryInvalidCredentialFailures")
    void keepsCredentialFailuresWithoutInfrastructureEvidenceUnauthenticated(Throwable failure) {
        assertThat(classifier.classifyAuthentication(failure))
                .isEqualTo(GatewayErrorCode.UNAUTHENTICATED);
    }

    @ParameterizedTest
    @MethodSource("globalAuthenticationMarkers")
    void globalAuthenticationMarkerTakesPrecedenceOverRoutedConnectionFailure(Throwable failure) {
        assertThat(classifier.classify(
                        routedExchange(),
                        new IllegalStateException(
                                "outer", new RuntimeException("middle", failure))))
                .contains(GatewayErrorCode.AUTHENTICATION_UNAVAILABLE);
    }

    @Test
    void rawTransportFailureDoesNotEstablishAuthenticationContextGlobally() {
        MockServerWebExchange exchange = exchange();

        assertThat(classifier.classify(exchange, new ConnectException("jwks.local:9443")))
                .contains(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
    }

    @Test
    void delegatesExplicitResponseStatusExceptions() {
        ResponseStatusException failure = new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "deferred timeout",
                new AuthenticationServiceException("authentication unavailable"));

        assertThat(classifier.classify(routedExchange(), nested(failure))).isEmpty();
    }

    @Test
    void doesNotClassifyAfterTheResponseIsCommitted() {
        MockServerWebExchange exchange = routedExchange();
        exchange.getResponse().setComplete().block();

        assertThat(classifier.classify(exchange, new ConnectException("refused"))).isEmpty();
    }

    @Test
    void safelyStopsWhenTheCauseChainContainsACycle() {
        RuntimeException first = new RuntimeException("first");
        RuntimeException second = new RuntimeException("second", first);
        first.initCause(second);

        assertThat(classifier.classify(exchange(), first))
                .contains(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
    }

    private static Stream<Throwable> routedTransportFailures() {
        return Stream.of(
                new ConnectException("refused"),
                new UnknownHostException("unknown"),
                new UnresolvedAddressException(),
                new ConnectTimeoutException("connect timeout"),
                PrematureCloseException.TEST_EXCEPTION);
    }

    private static Stream<Throwable> excludedRoutedFailures() {
        return Stream.of(
                new SocketTimeoutException("socket timeout"),
                ReadTimeoutException.INSTANCE,
                new IOException("generic I/O"),
                new ClosedChannelException(),
                new AbortedException("client aborted"));
    }

    private static Stream<Throwable> authenticationInfrastructureFailures() {
        return Stream.of(
                new AuthenticationServiceException("authentication service unavailable"),
                new JwtDecoderInitializationException(
                        "decoder unavailable", new IllegalStateException("initialization")),
                new WebClientRequestException(
                        new ConnectException("refused"),
                        HttpMethod.GET,
                        URI.create("https://issuer.invalid/jwks"),
                        HttpHeaders.EMPTY),
                new WebClientResponseException(
                        503,
                        "Service Unavailable",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        StandardCharsets.UTF_8),
                new ConnectException("refused"),
                new UnknownHostException("unknown"),
                new SocketTimeoutException("socket timeout"),
                new UnresolvedAddressException(),
                new ConnectTimeoutException("connect timeout"),
                ReadTimeoutException.INSTANCE,
                PrematureCloseException.TEST_EXCEPTION,
                new RemoteKeySourceException(
                        "remote key unavailable", new ConnectException("refused")));
    }

    private static Stream<Throwable> ordinaryInvalidCredentialFailures() {
        return Stream.of(
                new JwtException("invalid JWT"),
                new BadJwtException("bad JWT"),
                new InvalidBearerTokenException("invalid bearer token"),
                new OAuth2AuthenticationException(new OAuth2Error("invalid_token")));
    }

    private static Stream<Throwable> globalAuthenticationMarkers() {
        return Stream.of(
                new AuthenticationServiceException(
                        "authentication service unavailable",
                        new ConnectException("service.local:9443")),
                new JwtDecoderInitializationException(
                        "decoder unavailable",
                        new ConnectException("service.local:9443")),
                new RemoteKeySourceException(
                        "remote key unavailable",
                        new ConnectException("service.local:9443")));
    }

    private MockServerWebExchange routedExchange() {
        MockServerWebExchange exchange = exchange();
        exchange.getAttributes().put(GATEWAY_ROUTE_ATTR, mock(Route.class));
        exchange.getAttributes().put(
                GATEWAY_REQUEST_URL_ATTR,
                URI.create("http://product-service:8080/api/v1/catalog/products"));
        return exchange;
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products").build());
    }

    private Throwable nested(Throwable cause) {
        return new IllegalStateException("outer", new RuntimeException("middle", cause));
    }
}
