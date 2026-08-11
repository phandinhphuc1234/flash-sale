package com.philia.flashsale.flashsale.observability;

import com.philia.flashsale.flashsale.websupport.context.FlashSaleRequestContext;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.MDC;

/** Provides a valid W3C parent for non-HTTP control-plane calls when no request is active. */
public final class FlashSaleTraceContext {
    private static final SecureRandom RANDOM = new SecureRandom();

    private FlashSaleTraceContext() {
    }

    public static String currentOrGenerate() {
        String current = MDC.get(FlashSaleRequestContext.TRACEPARENT_HEADER);
        if (FlashSaleRequestContext.isValidTraceparent(current)) {
            return current;
        }
        byte[] traceId = new byte[16];
        byte[] spanId = new byte[8];
        RANDOM.nextBytes(traceId);
        RANDOM.nextBytes(spanId);
        return "00-" + HexFormat.of().formatHex(traceId)
                + "-" + HexFormat.of().formatHex(spanId) + "-01";
    }
}
