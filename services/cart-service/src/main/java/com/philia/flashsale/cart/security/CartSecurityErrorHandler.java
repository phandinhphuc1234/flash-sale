package com.philia.flashsale.cart.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.common.web.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** Writes stable, cache-disabled Cart security failures without leaking JWT validation details. */
@Component
public final class CartSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private final ObjectMapper objectMapper;

    public CartSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED,
                "UNAUTHENTICATED", "Authentication is required", true);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN,
                "CART_ACCESS_FORBIDDEN", "Cart access is forbidden", false);
    }

    private void write(HttpServletRequest request, HttpServletResponse response,
            int status, String code, String message, boolean authenticate) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(TRACE_HEADER, traceId(request));
        if (authenticate) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code, message));
    }

    private static String traceId(HttpServletRequest request) {
        String trace = request.getHeader(TRACE_HEADER);
        return trace == null || trace.isBlank() ? UUID.randomUUID().toString() : trace.trim();
    }
}
