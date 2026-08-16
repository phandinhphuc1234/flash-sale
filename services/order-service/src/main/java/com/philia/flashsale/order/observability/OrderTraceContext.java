package com.philia.flashsale.order.observability;

import com.philia.flashsale.order.websupport.context.OrderRequestContext;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.MDC;

/** W3C trace context helper for messages and scheduled work without an HTTP request. */
public final class OrderTraceContext {
    private static final SecureRandom RANDOM = new SecureRandom();

    private OrderTraceContext() {
    }

    public static String currentOrGenerate() {
        String current = MDC.get(OrderRequestContext.TRACEPARENT_HEADER);
        return OrderRequestContext.isValidTraceparent(current) ? current : generate();
    }

    public static String generate() {
        byte[] traceId = new byte[16];
        byte[] spanId = new byte[8];
        RANDOM.nextBytes(traceId);
        RANDOM.nextBytes(spanId);
        return "00-" + HexFormat.of().formatHex(traceId) + "-" + HexFormat.of().formatHex(spanId) + "-01";
    }
}
