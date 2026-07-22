package com.philia.flashsale.gateway.security;

import com.philia.flashsale.gateway.error.GatewayErrorCode;
import com.philia.flashsale.gateway.error.GatewayHttpErrorWriter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Translates reactive Spring Security failures at the gateway HTTP boundary. */
@Component
public final class GatewaySecurityErrorHandler
        implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final GatewayHttpErrorWriter errorWriter;

    public GatewaySecurityErrorHandler(GatewayHttpErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> commence(
            ServerWebExchange exchange,
            AuthenticationException authenticationException) {
        return errorWriter.write(exchange, GatewayErrorCode.UNAUTHENTICATED);
    }

    @Override
    public Mono<Void> handle(
            ServerWebExchange exchange,
            AccessDeniedException accessDeniedException) {
        return errorWriter.write(exchange, GatewayErrorCode.CATALOG_ADMIN_REQUIRED);
    }
}
