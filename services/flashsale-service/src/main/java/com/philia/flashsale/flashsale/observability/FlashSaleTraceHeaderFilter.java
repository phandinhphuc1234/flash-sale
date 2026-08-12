package com.philia.flashsale.flashsale.observability;

import com.philia.flashsale.flashsale.websupport.context.FlashSaleRequestContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Establishes the W3C trace context and header-only correlation response for every request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FlashSaleTraceHeaderFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceparent = request.getHeader(FlashSaleRequestContext.TRACEPARENT_HEADER);
        String traceId = FlashSaleRequestContext.isValidTraceparent(traceparent)
                ? traceparent.substring(3, 35)
                : FlashSaleRequestContext.normalizeOrGenerate(request.getHeader(FlashSaleRequestContext.TRACE_HEADER));
        request.setAttribute(FlashSaleRequestContext.TRACE_ATTRIBUTE, traceId);
        if (FlashSaleRequestContext.isValidTraceparent(traceparent)) {
            request.setAttribute(FlashSaleRequestContext.TRACEPARENT_ATTRIBUTE, traceparent);
        }
        response.setHeader(FlashSaleRequestContext.TRACE_HEADER, traceId);
        try (MDC.MDCCloseable ignored = MDC.putCloseable("traceId", traceId)) {
            filterChain.doFilter(request, response);
        }
    }
}
