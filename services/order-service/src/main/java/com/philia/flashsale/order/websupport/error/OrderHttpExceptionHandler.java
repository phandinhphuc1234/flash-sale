package com.philia.flashsale.order.websupport.error;

import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.common.web.FieldViolation;
import com.philia.flashsale.order.order.application.exception.InvalidOrderQueryException;
import com.philia.flashsale.order.order.application.exception.OrderNotFoundException;
import com.philia.flashsale.order.websupport.context.OrderTraceIdResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Sanitizes controller and persistence failures into the shared HTTP error envelope. */
@RestControllerAdvice
public class OrderHttpExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(OrderHttpExceptionHandler.class);
    private final OrderTraceIdResolver traceIds;

    public OrderHttpExceptionHandler(OrderTraceIdResolver traceIds) {
        this.traceIds = traceIds;
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(OrderNotFoundException exception, HttpServletRequest request) {
        return error(OrderErrorCode.ORDER_NOT_FOUND, request, null);
    }

    @ExceptionHandler(InvalidOrderQueryException.class)
    ResponseEntity<ApiErrorResponse> invalidQuery(InvalidOrderQueryException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.field() == null ? null
                : List.of(new FieldViolation(exception.field(), exception.getMessage()));
        return error(OrderErrorCode.VALIDATION_FAILED, request, violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> invalidType(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return error(OrderErrorCode.VALIDATION_FAILED, request,
                List.of(new FieldViolation(exception.getName(), "Invalid value")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        return error(OrderErrorCode.ORDER_METHOD_NOT_ALLOWED, request, null);
    }

    @ExceptionHandler(OrderAuthenticationException.class)
    ResponseEntity<ApiErrorResponse> authentication(OrderAuthenticationException exception,
            HttpServletRequest request) {
        return error(OrderErrorCode.AUTHENTICATION_REQUIRED, request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        OrderErrorCode code = OrderExceptionClassifier.classify(exception);
        if (code == OrderErrorCode.ORDER_INTERNAL_ERROR) {
            LOG.error("order_unexpected_failure traceId={} exceptionType={}",
                    traceIds.resolve(request), exception.getClass().getSimpleName());
        }
        return error(code, request, null);
    }

    private ResponseEntity<ApiErrorResponse> error(OrderErrorCode code, HttpServletRequest request,
            List<FieldViolation> violations) {
        return ResponseEntity.status(code.status())
                .header(OrderTraceIdResolver.TRACE_HEADER, traceIds.resolve(request))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code.name(), code.message(), violations));
    }
}
