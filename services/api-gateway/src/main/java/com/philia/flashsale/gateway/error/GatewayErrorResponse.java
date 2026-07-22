package com.philia.flashsale.gateway.error;

/** Stable HTTP body for failures produced by the gateway itself. */
public record GatewayErrorResponse(String code, String message, String traceId) {
}
