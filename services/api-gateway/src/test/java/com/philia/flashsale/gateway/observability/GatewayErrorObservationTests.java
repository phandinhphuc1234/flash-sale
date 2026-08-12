package com.philia.flashsale.gateway.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.philia.flashsale.gateway.error.GatewayErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class GatewayErrorObservationTests {

    private final GatewayErrorObservation observation = new GatewayErrorObservation();

    @Test
    void keepsOrdinaryCorrelationValuesReadable(CapturedOutput output) {
        observation.record(
                GatewayErrorCode.GATEWAY_INTERNAL_ERROR,
                "trace-123",
                "GET",
                "/api/v1/catalog/products",
                IllegalStateException.class.getName());

        assertThat(output.getOut())
                .contains("traceId=\"trace-123\"")
                .contains("path=\"/api/v1/catalog/products\"");
    }

    @Test
    void escapesControlCharactersBeforeTheyReachTheLog(CapturedOutput output) {
        observation.record(
                GatewayErrorCode.GATEWAY_INTERNAL_ERROR,
                "trace\r\nforged\"\\value\u0000",
                "GET",
                "/catalog/\u2028forged",
                IllegalStateException.class.getName());

        assertThat(output.getOut())
                .contains("traceId=\"trace\\r\\nforged\\\"\\\\value\\u0000\"")
                .contains("path=\"/catalog/\\u2028forged\"")
                .doesNotContain("trace\r\nforged")
                .doesNotContain("/catalog/\u2028forged");
    }
}
