package com.philia.flashsale.payment.websupport.error;

import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.payment.websupport.context.PaymentTraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Sanitizes Checkout failures and prevents provider/secret/URL leakage in errors. */
@RestControllerAdvice
public final class PaymentHttpExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PaymentHttpExceptionHandler.class);
    private final PaymentTraceIdResolver traceIds;

    public PaymentHttpExceptionHandler(PaymentTraceIdResolver traceIds) {
        this.traceIds = traceIds;
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiErrorResponse> missingHeader(MissingRequestHeaderException exception,
            HttpServletRequest request) {
        return error(PaymentErrorCode.VALIDATION_FAILED, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> invalidType(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return error(PaymentErrorCode.VALIDATION_FAILED, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return error(PaymentErrorCode.PAYMENT_METHOD_NOT_ALLOWED, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        PaymentErrorCode code = PaymentExceptionClassifier.classify(exception);
        if (code == PaymentErrorCode.PAYMENT_INTERNAL_ERROR) {
            LOG.error("payment_unexpected_failure traceId={} exceptionType={}",
                    traceIds.resolve(request), exception.getClass().getSimpleName());
        }
        return error(code, request);
    }

    private ResponseEntity<ApiErrorResponse> error(PaymentErrorCode code, HttpServletRequest request) {
        return ResponseEntity.status(code.status())
                .header(PaymentTraceIdResolver.TRACE_HEADER, traceIds.resolve(request))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code.name(), code.message()));
    }
}
