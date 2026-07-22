package com.philia.flashsale.product.adapter.in.web.admin;

import com.philia.flashsale.product.application.exception.AdminProductNotFoundException;
import com.philia.flashsale.product.application.exception.DuplicateProductCodeException;
import com.philia.flashsale.product.application.exception.DuplicateProductSlugException;
import com.philia.flashsale.product.application.exception.IdempotencyKeyReusedException;
import com.philia.flashsale.product.domain.exception.CatalogDomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ProductAdminController.class)
class ProductAdminExceptionHandler {

    // Translate application exceptions into stable HTTP errors for admin clients.
    @ExceptionHandler(AdminProductNotFoundException.class)
    ResponseEntity<AdminCatalogErrorResponse> productNotFound(
            AdminProductNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateProductCodeException.class)
    ResponseEntity<AdminCatalogErrorResponse> duplicateCode(
            DuplicateProductCodeException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_PRODUCT_CODE", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateProductSlugException.class)
    ResponseEntity<AdminCatalogErrorResponse> duplicateSlug(
            DuplicateProductSlugException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_PRODUCT_SLUG", exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<AdminCatalogErrorResponse> idempotencyConflict(
            IdempotencyKeyReusedException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage(), request);
    }

    // Domain failures are exposed by domain-owned error codes, not by framework exception names.
    @ExceptionHandler(CatalogDomainException.class)
    ResponseEntity<AdminCatalogErrorResponse> domainFailure(
            CatalogDomainException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AdminCatalogErrorResponse> invalidRequest(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<AdminCatalogErrorResponse> invalidBody(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ADMIN_REQUEST",
                "Admin request validation failed",
                request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<AdminCatalogErrorResponse> missingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ADMIN_REQUEST",
                "Required admin request header is missing: " + exception.getHeaderName(),
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<AdminCatalogErrorResponse> malformedBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ADMIN_REQUEST",
                "Admin request body is malformed",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<AdminCatalogErrorResponse> invalidArgumentType(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ADMIN_REQUEST",
                "Admin request contains an invalid value for " + exception.getName(),
                request);
    }

    // Echo the trace id so operators can connect a failed admin request to logs/traces later.
    private ResponseEntity<AdminCatalogErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(new AdminCatalogErrorResponse(code, message, request.getHeader("X-Trace-Id")));
    }
}
