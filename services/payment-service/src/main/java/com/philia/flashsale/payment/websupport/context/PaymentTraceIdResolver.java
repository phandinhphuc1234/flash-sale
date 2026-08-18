package com.philia.flashsale.payment.websupport.context;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Creates a bounded trace header for both successful and sanitized error responses. */
@Component
public final class PaymentTraceIdResolver {
    public static final String TRACE_HEADER = "X-Trace-Id";
    private static final String ATTRIBUTE = PaymentTraceIdResolver.class.getName();

    public String resolve(HttpServletRequest request) {
        Object current = request.getAttribute(ATTRIBUTE);
        if (current instanceof String value && safe(value)) {
            return value;
        }
        String incoming = request.getHeader(TRACE_HEADER);
        String trace = safe(incoming) ? incoming.trim() : UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, trace);
        return trace;
    }

    private boolean safe(String value) {
        return value != null && !value.isBlank() && value.length() <= 128
                && value.matches("[A-Za-z0-9._:-]+");
    }
}
