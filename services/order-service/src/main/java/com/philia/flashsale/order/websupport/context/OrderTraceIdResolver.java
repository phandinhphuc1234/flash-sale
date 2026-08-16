package com.philia.flashsale.order.websupport.context;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves a bounded correlation header for both successful and failed HTTP responses. */
@Component
public class OrderTraceIdResolver {
    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final String ATTRIBUTE = OrderTraceIdResolver.class.getName() + ".traceId";

    public String resolve(HttpServletRequest request) {
        Object existing = request.getAttribute(ATTRIBUTE);
        if (existing instanceof String value && safe(value)) {
            return value;
        }
        String incoming = request.getHeader(TRACE_HEADER);
        String value = safe(incoming == null ? "" : incoming.trim())
                ? incoming.trim() : UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(ATTRIBUTE, value);
        return value;
    }

    private boolean safe(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
                && value.matches("[A-Za-z0-9._:-]+");
    }
}
