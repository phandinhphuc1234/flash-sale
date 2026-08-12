package com.philia.flashsale.gateway.error;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.UnresolvedAddressException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.netty.channel.AbortedException;
import reactor.netty.http.client.PrematureCloseException;

/** Maps known gateway failure contexts to the small public error vocabulary. */
@Component
public final class GatewayFailureClassifier {

    private static final String REMOTE_KEY_SOURCE_EXCEPTION =
            "com.nimbusds.jose.RemoteKeySourceException";

    public Optional<GatewayErrorCode> classify(
            ServerWebExchange exchange,
            Throwable failure) {
        if (exchange.getResponse().isCommitted()) {
            return Optional.empty();
        }

        // Explicit HTTP status exceptions retain the behavior chosen by their original owner.
        if (hasCause(failure, ResponseStatusException.class::isInstance)) {
            return Optional.empty();
        }
        if (hasCause(failure, this::isGlobalAuthenticationMarker)) {
            return Optional.of(GatewayErrorCode.AUTHENTICATION_UNAVAILABLE);
        }
        if (isSelectedHttpRouteWithoutResponse(exchange)
                && isApprovedRoutedTransportFailure(failure)) {
            return Optional.of(GatewayErrorCode.DOWNSTREAM_UNAVAILABLE);
        }
        return Optional.of(GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
    }

    public GatewayErrorCode classifyAuthentication(Throwable failure) {
        return hasCause(failure, this::isAuthenticationInfrastructureFailure)
                ? GatewayErrorCode.AUTHENTICATION_UNAVAILABLE
                : GatewayErrorCode.UNAUTHENTICATED;
    }

    private boolean isSelectedHttpRouteWithoutResponse(ServerWebExchange exchange) {
        URI requestUrl = exchange.getAttribute(GATEWAY_REQUEST_URL_ATTR);
        return exchange.getAttribute(GATEWAY_ROUTE_ATTR) != null
                && requestUrl != null
                && ("http".equalsIgnoreCase(requestUrl.getScheme())
                        || "https".equalsIgnoreCase(requestUrl.getScheme()))
                && exchange.getAttribute(CLIENT_RESPONSE_ATTR) == null
                && exchange.getAttribute(CLIENT_RESPONSE_CONN_ATTR) == null;
    }

    private boolean isApprovedRoutedTransportFailure(Throwable failure) {
        // Client aborts and ambiguous I/O/timeouts must not be mislabeled as service outages.
        if (hasCause(failure, this::isExcludedRoutedFailure)) {
            return false;
        }
        return hasCause(failure, cause -> cause instanceof ConnectException
                || cause instanceof UnknownHostException
                || cause instanceof UnresolvedAddressException
                || cause instanceof ConnectTimeoutException
                || cause instanceof PrematureCloseException);
    }

    private boolean isExcludedRoutedFailure(Throwable cause) {
        if (cause instanceof SocketTimeoutException
                || cause instanceof ReadTimeoutException
                || cause instanceof ClosedChannelException
                || cause instanceof AbortedException) {
            return true;
        }
        return cause instanceof IOException
                && !(cause instanceof ConnectException)
                && !(cause instanceof UnknownHostException)
                && !(cause instanceof PrematureCloseException);
    }

    private boolean isGlobalAuthenticationMarker(Throwable cause) {
        return cause instanceof AuthenticationServiceException
                || cause instanceof JwtDecoderInitializationException
                || hasClassName(cause, REMOTE_KEY_SOURCE_EXCEPTION);
    }

    private boolean isAuthenticationInfrastructureFailure(Throwable cause) {
        return isGlobalAuthenticationMarker(cause)
                || cause instanceof WebClientRequestException
                || cause instanceof WebClientResponseException
                || cause instanceof ConnectException
                || cause instanceof UnknownHostException
                || cause instanceof SocketTimeoutException
                || cause instanceof UnresolvedAddressException
                || cause instanceof ConnectTimeoutException
                || cause instanceof ReadTimeoutException
                || cause instanceof PrematureCloseException;
    }

    private boolean hasClassName(Throwable cause, String className) {
        return cause.getClass().getName().equals(className);
    }

    private boolean hasCause(Throwable failure, Predicate<Throwable> predicate) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = failure;
        while (current != null && visited.add(current)) {
            if (predicate.test(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
