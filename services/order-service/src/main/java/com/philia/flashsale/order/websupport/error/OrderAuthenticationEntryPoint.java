package com.philia.flashsale.order.websupport.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.order.websupport.context.OrderTraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Writes a sanitized 401 response without exposing token validation details. */
@Component
public class OrderAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    private final OrderTraceIdResolver traceIds;

    public OrderAuthenticationEntryPoint(ObjectMapper objectMapper, OrderTraceIdResolver traceIds) {
        this.objectMapper = objectMapper;
        this.traceIds = traceIds;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(request, response, OrderErrorCode.AUTHENTICATION_REQUIRED, true);
    }

    void write(HttpServletRequest request, HttpServletResponse response, OrderErrorCode code,
            boolean authenticate) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(OrderTraceIdResolver.TRACE_HEADER, traceIds.resolve(request));
        if (authenticate) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code.name(), code.message()));
    }
}
