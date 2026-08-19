package com.philia.flashsale.payment.observability;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Small W3C trace-context value object used at HTTP and Kafka boundaries.
 *
 * <p>The application does not depend on a tracing SDK to carry the context. A valid incoming
 * parent is preserved; malformed or absent values create a new root context. MDC values are
 * installed only for the current request/record and are always restored by the closeable scope.
 */
public final class PaymentTraceContext {
    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String TRACESTATE_HEADER = "tracestate";
    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_TRACEPARENT = "traceparent";
    public static final String MDC_TRACESTATE = "tracestate";

    private static final Pattern TRACEPARENT = Pattern.compile(
            "^00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final Pattern TRACE_STATE = Pattern.compile("^[\\x20-\\x7e]{1,512}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String traceparent;
    private final String tracestate;

    private PaymentTraceContext(String traceparent, String tracestate) {
        this.traceparent = traceparent;
        this.tracestate = tracestate;
    }

    public static PaymentTraceContext fromHeaders(String incomingTraceparent, String incomingTracestate) {
        if (isValidTraceparent(incomingTraceparent)) {
            return new PaymentTraceContext(incomingTraceparent.toLowerCase(Locale.ROOT),
                    safeTracestate(incomingTracestate));
        }
        return root();
    }

    public static PaymentTraceContext root() {
        return new PaymentTraceContext("00-" + randomHex(16) + "-" + randomHex(8) + "-01", null);
    }

    public String traceparent() {
        return traceparent;
    }

    public String tracestate() {
        return tracestate;
    }

    public String traceId() {
        return traceparent.substring(3, 35);
    }

    public Scope openMdc() {
        return new Scope(this);
    }

    public Map<String, String> safeFields() {
        return tracestate == null
                ? Map.of(MDC_TRACE_ID, traceId(), MDC_TRACEPARENT, traceparent)
                : Map.of(MDC_TRACE_ID, traceId(), MDC_TRACEPARENT, traceparent,
                        MDC_TRACESTATE, tracestate);
    }

    public static boolean isValidTraceparent(String value) {
        if (value == null || !TRACEPARENT.matcher(value).matches()) {
            return false;
        }
        return !value.substring(3, 35).chars().allMatch(character -> character == '0')
                && !value.substring(36, 52).chars().allMatch(character -> character == '0');
    }

    private static String safeTracestate(String value) {
        return value != null && TRACE_STATE.matcher(value).matches() ? value : null;
    }

    private static String randomHex(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        StringBuilder result = new StringBuilder(bytes * 2);
        for (byte item : value) {
            result.append(String.format(Locale.ROOT, "%02x", item));
        }
        return result.toString();
    }

    public static final class Scope implements AutoCloseable {
        private final String previousTraceId;
        private final String previousTraceparent;
        private final String previousTracestate;

        private Scope(PaymentTraceContext context) {
            previousTraceId = MDC.get(MDC_TRACE_ID);
            previousTraceparent = MDC.get(MDC_TRACEPARENT);
            previousTracestate = MDC.get(MDC_TRACESTATE);
            MDC.put(MDC_TRACE_ID, context.traceId());
            MDC.put(MDC_TRACEPARENT, context.traceparent());
            if (context.tracestate() != null) {
                MDC.put(MDC_TRACESTATE, context.tracestate());
            } else {
                MDC.remove(MDC_TRACESTATE);
            }
        }

        @Override
        public void close() {
            restore(MDC_TRACE_ID, previousTraceId);
            restore(MDC_TRACEPARENT, previousTraceparent);
            restore(MDC_TRACESTATE, previousTracestate);
        }

        private void restore(String key, String value) {
            if (value == null) {
                MDC.remove(key);
            } else {
                MDC.put(key, value);
            }
        }
    }
}
