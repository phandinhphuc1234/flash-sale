package com.philia.flashsale.order.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import java.util.Objects;
import org.springframework.http.HttpHeaders;

/** Adds only Order's short-lived machine token to its future internal Feign calls. */
public final class OrderInternalAuthorizationRequestInterceptor implements RequestInterceptor {

    private final OrderInternalServiceTokenManager tokenManager;

    public OrderInternalAuthorizationRequestInterceptor(OrderInternalServiceTokenManager tokenManager) {
        this.tokenManager = Objects.requireNonNull(tokenManager);
    }

    @Override
    public void apply(RequestTemplate template) {
        template.header(HttpHeaders.AUTHORIZATION, tokenManager.authorizationHeader());
    }
}
