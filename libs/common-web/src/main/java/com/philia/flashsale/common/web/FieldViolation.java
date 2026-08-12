package com.philia.flashsale.common.web;

/**
 * Describes one invalid input field in an HTTP validation error response.
 *
 * @param field   request field or parameter that failed validation
 * @param message client-safe explanation of the validation failure
 */
public record FieldViolation(String field, String message) {
}
