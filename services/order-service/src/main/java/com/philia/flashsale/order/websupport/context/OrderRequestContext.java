package com.philia.flashsale.order.websupport.context;

import java.util.UUID;

/** Shared HTTP/message trace context names and strict W3C header validation. */
public final class OrderRequestContext {
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";
    public static final String TRACE_ATTRIBUTE = OrderRequestContext.class.getName() + ".traceId";
    public static final String TRACEPARENT_ATTRIBUTE = OrderRequestContext.class.getName() + ".traceparent";
    public static final String TRACESTATE_ATTRIBUTE = OrderRequestContext.class.getName() + ".tracestate";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private OrderRequestContext() {
    }

    public static boolean isValidTraceparent(String value) {
        if (value == null || !value.matches("^[0-9a-f]{2}-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$")) {
            return false;
        }
        return !value.substring(3, 35).equals("00000000000000000000000000000000")
                && !value.substring(36, 52).equals("0000000000000000");
    }

    public static String normalizeOrGenerate(String value) {
        if (value != null && value.matches("[A-Za-z0-9._:-]{1,128}")) {
            return value;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String normalizeTracestate(String value) {
        if (value == null || value.isBlank() || value.length() > 512 || !value.matches("[\\x20-\\x7E]+")) {
            return null;
        }
        return value;
    }
}
