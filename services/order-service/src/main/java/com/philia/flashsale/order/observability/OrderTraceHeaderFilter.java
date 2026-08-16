package com.philia.flashsale.order.observability;

import com.philia.flashsale.order.websupport.context.OrderRequestContext;
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

/** Restores W3C request context, emits a safe trace response header, and scopes MDC to one request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class OrderTraceHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String incomingTraceparent = request.getHeader(OrderRequestContext.TRACEPARENT_HEADER);
        String traceparent = OrderRequestContext.isValidTraceparent(incomingTraceparent)
                ? incomingTraceparent : OrderTraceContext.generate();
        String traceId = traceparent.substring(3, 35);
        String tracestate = OrderRequestContext.normalizeTracestate(
                request.getHeader(OrderRequestContext.TRACESTATE_HEADER));

        request.setAttribute(OrderRequestContext.TRACE_ATTRIBUTE, traceId);
        request.setAttribute(OrderRequestContext.TRACEPARENT_ATTRIBUTE, traceparent);
        if (tracestate != null) {
            request.setAttribute(OrderRequestContext.TRACESTATE_ATTRIBUTE, tracestate);
        }
        response.setHeader(OrderRequestContext.TRACE_HEADER, traceId);
        try (MDC.MDCCloseable trace = MDC.putCloseable(OrderRequestContext.TRACE_ID_MDC_KEY, traceId);
                MDC.MDCCloseable parent = MDC.putCloseable(OrderRequestContext.TRACEPARENT_HEADER, traceparent);
                MDC.MDCCloseable state = MDC.putCloseable(OrderRequestContext.TRACESTATE_HEADER,
                        tracestate == null ? "" : tracestate)) {
            filterChain.doFilter(request, response);
        }
    }
}
