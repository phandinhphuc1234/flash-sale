package com.philia.flashsale.gateway.security;

import com.philia.flashsale.gateway.error.GatewayHttpErrorWriter;
import com.philia.flashsale.gateway.error.GatewayErrorCode;
import com.philia.flashsale.gateway.error.GatewayFailureClassifier;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Translates reactive Spring Security failures at the gateway HTTP boundary.
 */
@Component
public final class GatewaySecurityErrorHandler
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final GatewayHttpErrorWriter errorWriter;
    private final GatewayFailureClassifier failureClassifier;

    public GatewaySecurityErrorHandler(
            GatewayHttpErrorWriter errorWriter,
            GatewayFailureClassifier failureClassifier) {
        this.errorWriter = errorWriter;
        this.failureClassifier = failureClassifier;
    }

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException authenticationException) {
        GatewayErrorCode errorCode = failureClassifier.classifyAuthentication(authenticationException);
        if (errorCode == GatewayErrorCode.UNAUTHENTICATED) {
            exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        } else {
            exchange.getResponse().getHeaders().remove(HttpHeaders.WWW_AUTHENTICATE);
        }
        return errorWriter.write(exchange, errorCode, authenticationException);
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException accessDeniedException) {
        GatewayErrorCode errorCode = isAdminCatalogPath(exchange)
                ? GatewayErrorCode.CATALOG_ADMIN_REQUIRED
                : GatewayErrorCode.ACCESS_DENIED;
        return errorWriter.write(exchange, errorCode, accessDeniedException);
    }

    private static boolean isAdminCatalogPath(ServerWebExchange exchange) {
        String path = exchange.getRequest().getPath().value();
        return path.equals("/api/v1/admin/catalog")
                || path.startsWith("/api/v1/admin/catalog/");
    }
}
