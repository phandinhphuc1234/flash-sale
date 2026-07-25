package com.philia.flashsale.gateway.observability;

import com.philia.flashsale.gateway.error.GatewayErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Emits only bounded metadata for gateway-owned failures. */
@Component
public final class GatewayErrorObservation {

    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final Logger LOGGER = LoggerFactory.getLogger(GatewayErrorObservation.class);

    public void record(
            GatewayErrorCode errorCode,
            String traceId,
            String method,
            String path,
            String causeClassName) {
        LOGGER.warn(
                "gateway_error code={} status={} traceId=\"{}\" method={} path=\"{}\" cause={}",
                errorCode.name(),
                errorCode.status().value(),
                escapeLogValue(traceId),
                method,
                escapeLogValue(path),
                causeClassName);
    }

    /** Keeps correlation values readable while preventing control characters from forging log lines. */
    static String escapeLogValue(String value) {
        if (value == null) {
            return null;
        }

        StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(character)
                            || character == '\u2028'
                            || character == '\u2029') {
                        appendUnicodeEscape(escaped, character);
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static void appendUnicodeEscape(StringBuilder target, char character) {
        target.append("\\u");
        target.append(HEX[(character >>> 12) & 0x0f]);
        target.append(HEX[(character >>> 8) & 0x0f]);
        target.append(HEX[(character >>> 4) & 0x0f]);
        target.append(HEX[character & 0x0f]);
    }
}
