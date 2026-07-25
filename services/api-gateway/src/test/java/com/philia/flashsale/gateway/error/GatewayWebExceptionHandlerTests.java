package com.philia.flashsale.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

class GatewayWebExceptionHandlerTests {

    private final GatewayFailureClassifier classifier = mock(GatewayFailureClassifier.class);
    private final GatewayHttpErrorWriter writer = mock(GatewayHttpErrorWriter.class);
    private final GatewayWebExceptionHandler handler =
            new GatewayWebExceptionHandler(classifier, writer);

    @Test
    void runsBeforeBootsDefaultReactiveErrorHandler() {
        assertThat(handler).isInstanceOf(Ordered.class);
        assertThat(((Ordered) handler).getOrder()).isEqualTo(-2);
    }

    @Test
    void rendersTheClassifiedGatewayFailure() {
        MockServerWebExchange exchange = exchange();
        IllegalStateException failure = new IllegalStateException("sensitive detail");
        when(classifier.classify(exchange, failure))
                .thenReturn(Optional.of(GatewayErrorCode.GATEWAY_INTERNAL_ERROR));
        when(writer.write(exchange, GatewayErrorCode.GATEWAY_INTERNAL_ERROR, failure))
                .thenReturn(Mono.empty());

        handler.handle(exchange, failure).block(Duration.ofSeconds(5));

        verify(writer).write(exchange, GatewayErrorCode.GATEWAY_INTERNAL_ERROR, failure);
    }

    @Test
    void delegatesAnExplicitResponseStatusExceptionWithoutWriting() {
        MockServerWebExchange exchange = exchange();
        ResponseStatusException failure =
                new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT);
        when(classifier.classify(exchange, failure)).thenReturn(Optional.empty());

        Throwable propagated = catchThrowable(
                () -> handler.handle(exchange, failure).block(Duration.ofSeconds(5)));

        assertThat(propagated).isSameAs(failure);
        verifyNoInteractions(writer);
    }

    @Test
    void propagatesTheOriginalFailureWhenTheResponseIsAlreadyCommitted() {
        MockServerWebExchange exchange = exchange();
        exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
        exchange.getResponse().setComplete().block(Duration.ofSeconds(5));
        IllegalStateException failure = new IllegalStateException("late failure");

        Throwable propagated = catchThrowable(
                () -> handler.handle(exchange, failure).block(Duration.ofSeconds(5)));

        assertThat(propagated).isSameAs(failure);
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verifyNoInteractions(classifier, writer);
    }

    private MockServerWebExchange exchange() {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/catalog/products").build());
    }
}
