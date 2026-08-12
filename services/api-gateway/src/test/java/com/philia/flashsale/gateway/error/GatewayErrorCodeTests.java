package com.philia.flashsale.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;

class GatewayErrorCodeTests {

    @ParameterizedTest
    @MethodSource("approvedTaxonomy")
    void exposesTheExactApprovedStatusAndSafeMessage(
            GatewayErrorCode code,
            HttpStatus status,
            String message) {
        assertThat(code.status()).isEqualTo(status);
        assertThat(code.message()).isEqualTo(message);
    }

    @Test
    void containsOnlyTheNineGatewayOwnedCodes() {
        assertThat(GatewayErrorCode.values())
                .containsExactly(
                        GatewayErrorCode.INVALID_ADMIN_REQUEST,
                        GatewayErrorCode.UNAUTHENTICATED,
                        GatewayErrorCode.CATALOG_ADMIN_REQUIRED,
                        GatewayErrorCode.INVENTORY_ADMIN_REQUIRED,
                        GatewayErrorCode.ACCESS_DENIED,
                        GatewayErrorCode.RATE_LIMIT_EXCEEDED,
                        GatewayErrorCode.DOWNSTREAM_UNAVAILABLE,
                        GatewayErrorCode.AUTHENTICATION_UNAVAILABLE,
                        GatewayErrorCode.GATEWAY_INTERNAL_ERROR);
    }

    private static Stream<Arguments> approvedTaxonomy() {
        return Stream.of(
                Arguments.of(
                        GatewayErrorCode.INVALID_ADMIN_REQUEST,
                        HttpStatus.BAD_REQUEST,
                        "X-Trace-Id must be non-blank and no longer than 128 characters"),
                Arguments.of(
                        GatewayErrorCode.UNAUTHENTICATED,
                        HttpStatus.UNAUTHORIZED,
                        "Authentication is required"),
                Arguments.of(
                        GatewayErrorCode.CATALOG_ADMIN_REQUIRED,
                        HttpStatus.FORBIDDEN,
                        "CATALOG_ADMIN authority is required"),
                Arguments.of(
                        GatewayErrorCode.INVENTORY_ADMIN_REQUIRED,
                        HttpStatus.FORBIDDEN,
                        "INVENTORY_ADMIN authority is required"),
                Arguments.of(
                        GatewayErrorCode.ACCESS_DENIED,
                        HttpStatus.FORBIDDEN,
                        "Access is denied"),
                Arguments.of(
                        GatewayErrorCode.RATE_LIMIT_EXCEEDED,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Too many requests"),
                Arguments.of(
                        GatewayErrorCode.DOWNSTREAM_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The requested service is temporarily unavailable"),
                Arguments.of(
                        GatewayErrorCode.AUTHENTICATION_UNAVAILABLE,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Authentication is temporarily unavailable"),
                Arguments.of(
                        GatewayErrorCode.GATEWAY_INTERNAL_ERROR,
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "The gateway could not process the request"));
    }
}
