package com.philia.flashsale.payment.websupport.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.payment.websupport.context.PaymentTraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** Writes a stable 401 without exposing JWT validation/provider details. */
@Component
public final class PaymentAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    private final PaymentTraceIdResolver traceIds;

    public PaymentAuthenticationEntryPoint(ObjectMapper objectMapper, PaymentTraceIdResolver traceIds) {
        this.objectMapper = objectMapper;
        this.traceIds = traceIds;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(request, response, PaymentErrorCode.AUTHENTICATION_REQUIRED, true);
    }

    void write(HttpServletRequest request, HttpServletResponse response, PaymentErrorCode code,
            boolean authenticate) throws IOException {
        if (response.isCommitted()) return;
        response.setStatus(code.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(PaymentTraceIdResolver.TRACE_HEADER, traceIds.resolve(request));
        if (authenticate) response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code.name(), code.message()));
    }
}
