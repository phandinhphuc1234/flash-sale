package com.philia.flashsale.flashsale.websupport.context;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/** Bounded request correlation values shared by the trace filter and HTTP writers. */
public final class FlashSaleRequestContext {
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACE_ATTRIBUTE = FlashSaleRequestContext.class.getName() + ".traceId";
    public static final String TRACEPARENT_ATTRIBUTE = FlashSaleRequestContext.class.getName() + ".traceparent";
    public static final int MAX_TRACE_LENGTH = 128;

    private FlashSaleRequestContext() {
    }

    /** Keeps a safe caller value or creates a new bounded correlation ID. */
    public static String normalizeOrGenerate(String incoming) {
        String candidate = incoming == null ? "" : incoming.trim();
        return isSafe(candidate) ? candidate : UUID.randomUUID().toString().replace("-", "");
    }

    /** Returns the filter value, with a safe fallback for early framework failures. */
    public static String resolveTraceId(HttpServletRequest request) {
        Object value = request.getAttribute(TRACE_ATTRIBUTE);
        if (value instanceof String traceId && isSafe(traceId)) {
            return traceId;
        }
        String traceId = normalizeOrGenerate(request.getHeader(TRACE_HEADER));
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        return traceId;
    }

    public static boolean isValidTraceparent(String value) {
        return value != null && value.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")
                && !value.substring(3, 35).matches("0{32}")
                && !value.substring(36, 52).matches("0{16}");
    }

    private static boolean isSafe(String value) {
        return !value.isBlank() && value.length() <= MAX_TRACE_LENGTH
                && value.matches("[A-Za-z0-9._:-]+");
    }
}
