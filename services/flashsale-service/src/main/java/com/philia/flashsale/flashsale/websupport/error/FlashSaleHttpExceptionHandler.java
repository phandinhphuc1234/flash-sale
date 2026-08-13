package com.philia.flashsale.flashsale.websupport.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.common.web.FieldViolation;
import com.philia.flashsale.flashsale.websupport.context.FlashSaleRequestContext;
import com.philia.flashsale.flashsale.reservation.application.exception.ReservationSubmissionException;
import com.philia.flashsale.flashsale.reservation.application.result.ReservationSubmissionResult;
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
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    @ExceptionHandler(FlashSaleInvalidIdempotencyKeyException.class)
    ResponseEntity<ApiErrorResponse> invalidIdempotencyKey(FlashSaleInvalidIdempotencyKeyException exception,
            HttpServletRequest request) {
        return error(FlashSaleErrorCode.IDEMPOTENCY_KEY_REQUIRED, request, null);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> malformedRequest(Exception exception, HttpServletRequest request) {
        return error(FlashSaleErrorCode.VALIDATION_FAILED, request, null);
    }

    @ExceptionHandler(ReservationSubmissionException.class)
    ResponseEntity<ApiErrorResponse> reservationOutcome(ReservationSubmissionException exception,
            HttpServletRequest request) {
        FlashSaleErrorCode code = switch (exception.outcome()) {
            case IDEMPOTENCY_CONFLICT -> FlashSaleErrorCode.FLASH_SALE_IDEMPOTENCY_CONFLICT;
            case CAMPAIGN_NOT_ACTIVE, CAMPAIGN_NOT_STARTED, CAMPAIGN_ENDED ->
                    FlashSaleErrorCode.FLASH_SALE_CAMPAIGN_NOT_ACTIVE;
            case RESERVATION_EXPIRED -> FlashSaleErrorCode.FLASH_SALE_RESERVATION_EXPIRED;
            case VARIANT_NOT_ELIGIBLE -> FlashSaleErrorCode.FLASH_SALE_VARIANT_NOT_ELIGIBLE;
            case SOLD_OUT -> FlashSaleErrorCode.FLASH_SALE_SOLD_OUT;
            case PURCHASE_LIMIT_EXCEEDED -> FlashSaleErrorCode.FLASH_SALE_PURCHASE_LIMIT_EXCEEDED;
            case PROJECTION_UNAVAILABLE -> FlashSaleErrorCode.FLASH_SALE_PROJECTION_UNAVAILABLE;
            case REDIS_UNAVAILABLE -> FlashSaleErrorCode.FLASH_SALE_REDIS_UNAVAILABLE;
            case ACCEPTANCE_PENDING -> FlashSaleErrorCode.FLASH_SALE_ACCEPTANCE_PENDING;
            case CAMPAIGN_UNKNOWN, CAMPAIGN_RECOVERY_REQUIRED ->
                    FlashSaleErrorCode.FLASH_SALE_PROJECTION_UNAVAILABLE;
            case ACCEPTED_NEW, ACCEPTED_REPLAY -> FlashSaleErrorCode.INTERNAL_ERROR;
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.status())
                .header(FlashSaleRequestContext.TRACE_HEADER, FlashSaleRequestContext.resolveTraceId(request))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON);
        if (exception.outcome() == ReservationSubmissionResult.Outcome.ACCEPTANCE_PENDING) {
            builder.header(HttpHeaders.RETRY_AFTER, "1");
        }
        return builder.body(ApiErrorResponse.of(code.name(), code.message()));
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
