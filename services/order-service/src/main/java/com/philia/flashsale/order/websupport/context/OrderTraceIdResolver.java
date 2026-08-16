package com.philia.flashsale.order.websupport.context;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** Resolves a bounded correlation header for both successful and failed HTTP responses. */
@Component
public class OrderTraceIdResolver {
    public static final String TRACE_HEADER = OrderRequestContext.TRACE_HEADER;

    public String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(OrderRequestContext.TRACE_ATTRIBUTE);
        if (existing instanceof String value && safe(value)) {
            return value;
        }
        String incoming = request.getHeader(TRACE_HEADER);
        String value = safe(incoming == null ? "" : incoming.trim())
                ? incoming.trim() : OrderRequestContext.normalizeOrGenerate(incoming);
        request.setAttribute(OrderRequestContext.TRACE_ATTRIBUTE, value);
        return value;
    }

    private boolean safe(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
                && value.matches("[A-Za-z0-9._:-]+");
    }
}
