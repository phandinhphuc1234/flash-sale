package com.philia.flashsale.payment.websupport.context;

import com.philia.flashsale.payment.observability.PaymentObservability;
import com.philia.flashsale.payment.observability.PaymentTraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Servlet boundary for trace/MDC lifecycle and safe request context.
 *
 * <p>Only validated W3C context and the bounded trace response header cross the boundary. Request
 * bodies, authorization headers, provider URLs, and identifiers are intentionally not copied to
 * MDC or logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class PaymentRequestContext extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentRequestContext.class);
    private final PaymentTraceIdResolver traceIds;

    public PaymentRequestContext(PaymentTraceIdResolver traceIds) {
        this.traceIds = traceIds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String traceparent = request.getHeader(PaymentTraceContext.TRACEPARENT_HEADER);
        String tracestate = request.getHeader(PaymentTraceContext.TRACESTATE_HEADER);
        PaymentTraceContext context = PaymentTraceContext.fromHeaders(traceparent, tracestate);
        String traceId = traceIds.resolve(request);
        response.setHeader(PaymentTraceIdResolver.TRACE_HEADER,
                PaymentObservability.safeIdentifier(traceId));
        try (PaymentTraceContext.Scope ignored = context.openMdc()) {
            filterChain.doFilter(request, response);
        } catch (RuntimeException | Error exception) {
            LOG.debug("payment_request_failed traceCategory={} exceptionType={}",
                    PaymentObservability.safeCategory(request.getMethod()),
                    PaymentObservability.redactException(exception));
            throw exception;
        }
    }
}
