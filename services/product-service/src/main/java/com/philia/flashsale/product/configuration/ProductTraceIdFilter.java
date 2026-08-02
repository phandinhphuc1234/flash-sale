package com.philia.flashsale.product.configuration;

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

/** Propagates a bounded correlation id and exposes it only as the HTTP response header. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class ProductTraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final int MAX_LENGTH = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String supplied = request.getHeader(TRACE_HEADER);
        String traceId = supplied == null || supplied.isBlank() || supplied.length() > MAX_LENGTH
                ? UUID.randomUUID().toString()
                : supplied.trim();
        response.setHeader(TRACE_HEADER, traceId);
        filterChain.doFilter(request, response);
    }
}
