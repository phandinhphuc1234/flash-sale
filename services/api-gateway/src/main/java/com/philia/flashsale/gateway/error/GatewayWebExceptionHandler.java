package com.philia.flashsale.gateway.error;

import java.util.Optional;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/** Renders unhandled gateway failures before Spring Boot's default reactive error handler. */
@Component
public final class GatewayWebExceptionHandler implements WebExceptionHandler, Ordered {

    private final GatewayFailureClassifier failureClassifier;
    private final GatewayHttpErrorWriter errorWriter;

    public GatewayWebExceptionHandler(
            GatewayFailureClassifier failureClassifier,
            GatewayHttpErrorWriter errorWriter) {
        this.failureClassifier = failureClassifier;
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable failure) {
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(failure);
        }

        Optional<GatewayErrorCode> errorCode = failureClassifier.classify(exchange, failure);
        return errorCode
                .map(code -> errorWriter.write(exchange, code, failure))
                .orElseGet(() -> Mono.error(failure));
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
