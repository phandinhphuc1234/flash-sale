package com.philia.flashsale.inventory.configuration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Propagates a bounded correlation ID and keeps it in the HTTP header only. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InventoryTraceIdFilter extends OncePerRequestFilter {

    static final String TRACE_HEADER = "X-Trace-Id";
    static final int MAX_TRACE_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_HEADER);
        String candidate = incoming == null ? "" : incoming.trim();
        String traceId = isValid(candidate) ? candidate : UUID.randomUUID().toString();
        response.setHeader(TRACE_HEADER, traceId);
        filterChain.doFilter(request, response);
    }

    private boolean isValid(String value) {
        return !value.isBlank()
                && value.length() <= MAX_TRACE_LENGTH
                && value.matches("[A-Za-z0-9._:-]+");
    }
}
