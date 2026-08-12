package com.philia.flashsale.campaign.websupport.context;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/** Request-scoped correlation context shared by filters and HTTP error writers. */
public final class CampaignRequestContext {

    public static final String TRACE_ATTRIBUTE = CampaignRequestContext.class.getName() + ".traceId";
    public static final String TRACE_HEADER = "X-Trace-Id";
    public static final int MAX_TRACE_LENGTH = 128;

    private CampaignRequestContext() {
    }

    /** Normalizes a safe caller value or creates a server correlation value. */
    public static String normalizeOrGenerate(String incoming) {
        String candidate = incoming == null ? "" : incoming.trim();
        return isValid(candidate) ? candidate : UUID.randomUUID().toString();
    }

    /** Returns the filter value, creating one for early failures when no filter ran. */
    public static String resolveTraceId(HttpServletRequest request) {
        Object value = request.getAttribute(TRACE_ATTRIBUTE);
        if (value instanceof String traceId && isValid(traceId)) {
            return traceId;
        }
        String traceId = normalizeOrGenerate(request.getHeader(TRACE_HEADER));
        request.setAttribute(TRACE_ATTRIBUTE, traceId);
        return traceId;
    }

    private static boolean isValid(String value) {
        return !value.isBlank()
                && value.length() <= MAX_TRACE_LENGTH
                && value.matches("[A-Za-z0-9._:-]+");
    }
}
