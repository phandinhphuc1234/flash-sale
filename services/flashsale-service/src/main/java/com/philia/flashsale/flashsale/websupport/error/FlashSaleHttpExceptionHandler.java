package com.philia.flashsale.flashsale.websupport.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.common.web.FieldViolation;
import com.philia.flashsale.flashsale.websupport.context.FlashSaleRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;

/** Translates application/framework failures into the shared, sanitized HTTP envelope. */
@RestControllerAdvice
public class FlashSaleHttpExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(FlashSaleHttpExceptionHandler.class);
    private final ObjectMapper objectMapper;

    public FlashSaleHttpExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(),
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage()))
                .collect(Collectors.toList());
        return error(FlashSaleErrorCode.VALIDATION_FAILED, request, violations);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiErrorResponse> missingHeader(MissingRequestHeaderException exception,
            HttpServletRequest request) {
        return error("Idempotency-Key".equalsIgnoreCase(exception.getHeaderName())
                ? FlashSaleErrorCode.IDEMPOTENCY_KEY_REQUIRED : FlashSaleErrorCode.VALIDATION_FAILED,
                request, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(FlashSaleErrorCode.VALIDATION_FAILED, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return error(FlashSaleErrorCode.INTERNAL_ERROR, request, null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> unsupportedMediaType(HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request) {
        return error(FlashSaleErrorCode.VALIDATION_FAILED, request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        FlashSaleErrorCode code = FlashSaleExceptionClassifier.classify(exception);
        LOG.error("flashsale_unexpected_failure traceId={} exceptionType={}",
                FlashSaleRequestContext.resolveTraceId(request), exception.getClass().getSimpleName());
        return error(code, request, null);
    }

    private ResponseEntity<ApiErrorResponse> error(FlashSaleErrorCode code, HttpServletRequest request,
            List<FieldViolation> violations) {
        String traceId = FlashSaleRequestContext.resolveTraceId(request);
        return ResponseEntity.status(code.status())
                .header(FlashSaleRequestContext.TRACE_HEADER, traceId)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code.name(), code.message(), violations));
    }

    static void writeSecurityError(HttpServletRequest request, HttpServletResponse response,
            ObjectMapper objectMapper, FlashSaleErrorCode code, boolean authenticationRequired)
            throws IOException {
        if (response.isCommitted()) {
            return;
        }
        String traceId = FlashSaleRequestContext.resolveTraceId(request);
        response.setStatus(code.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(FlashSaleRequestContext.TRACE_HEADER, traceId);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        if (authenticationRequired) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code.name(), code.message()));
    }
}
