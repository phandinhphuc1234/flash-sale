package com.philia.flashsale.cart.websupport.error;

import com.philia.flashsale.cart.adapter.in.web.CartController;
import com.philia.flashsale.cart.application.exception.CartVariantNotFoundException;
import com.philia.flashsale.cart.application.exception.CartVariantNotSellableException;
import com.philia.flashsale.cart.application.exception.ProductDisplayDependencyException;
import com.philia.flashsale.cart.security.InvalidCartPrincipalException;
import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.common.web.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/** Translates Cart application failures into the documented shared HTTP envelope. */
@RestControllerAdvice(assignableTypes = CartController.class)
public class CartHttpExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(CartHttpExceptionHandler.class);

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<ApiErrorResponse> validation(Exception exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception instanceof MethodArgumentNotValidException invalid
                ? invalid.getBindingResult().getFieldErrors().stream()
                        .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                        .toList()
                : null;
        return error(CartErrorCode.CART_VALIDATION_ERROR, request, violations);
    }

    @ExceptionHandler(CartVariantNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(HttpServletRequest request) {
        return error(CartErrorCode.CART_VARIANT_NOT_FOUND, request, null);
    }

    @ExceptionHandler(CartVariantNotSellableException.class)
    ResponseEntity<ApiErrorResponse> notSellable(HttpServletRequest request) {
        return error(CartErrorCode.CART_VARIANT_NOT_SELLABLE, request, null);
    }

    @ExceptionHandler(ProductDisplayDependencyException.class)
    ResponseEntity<ApiErrorResponse> productUnavailable(HttpServletRequest request) {
        return error(CartErrorCode.CART_PRODUCT_DEPENDENCY_UNAVAILABLE, request, null);
    }

    @ExceptionHandler(InvalidCartPrincipalException.class)
    ResponseEntity<ApiErrorResponse> principal(HttpServletRequest request) {
        return error(CartErrorCode.UNAUTHENTICATED, request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpServletRequest request) {
        return error(CartErrorCode.CART_VALIDATION_ERROR, request, null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> unexpected(Exception exception, HttpServletRequest request) {
        LOG.error("cart_unexpected_failure traceId={} exceptionType={}", traceId(request),
                exception.getClass().getSimpleName());
        return error(CartErrorCode.CART_INTERNAL_ERROR, request, null);
    }

    private ResponseEntity<ApiErrorResponse> error(CartErrorCode code, HttpServletRequest request,
            List<FieldViolation> violations) {
        return ResponseEntity.status(code.status()).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(CartController.TRACE_HEADER, traceId(request))
                .contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorResponse.of(code.name(), code.message(), violations));
    }

    private String traceId(HttpServletRequest request) {
        String value = request.getHeader(CartController.TRACE_HEADER);
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value.trim();
    }
}
