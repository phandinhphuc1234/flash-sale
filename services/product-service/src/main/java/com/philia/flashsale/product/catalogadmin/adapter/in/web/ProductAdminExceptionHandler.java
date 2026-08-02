package com.philia.flashsale.product.catalogadmin.adapter.in.web;

import com.philia.flashsale.product.catalogadmin.application.exception.AdminProductNotFoundException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateProductCodeException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateProductSlugException;
import com.philia.flashsale.product.catalogadmin.application.exception.IdempotencyKeyReusedException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateVariantSkuException;
import com.philia.flashsale.product.catalogadmin.application.exception.DuplicateVariantBarcodeException;
import com.philia.flashsale.product.catalogadmin.application.exception.CategoryNotFoundException;
import com.philia.flashsale.product.catalogadmin.application.exception.StaleProductVersionException;
import com.philia.flashsale.product.catalogadmin.domain.exception.CatalogDomainException;
import com.philia.flashsale.common.web.ApiErrorResponse;
import com.philia.flashsale.common.web.FieldViolation;
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
    ResponseEntity<ApiErrorResponse> productNotFound(
            AdminProductNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateProductCodeException.class)
    ResponseEntity<ApiErrorResponse> duplicateCode(
            DuplicateProductCodeException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_PRODUCT_CODE", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateProductSlugException.class)
    ResponseEntity<ApiErrorResponse> duplicateSlug(
            DuplicateProductSlugException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_PRODUCT_SLUG", exception.getMessage(), request);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<ApiErrorResponse> idempotencyConflict(
            IdempotencyKeyReusedException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateVariantSkuException.class)
    ResponseEntity<ApiErrorResponse> duplicateVariantSku(
            DuplicateVariantSkuException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_VARIANT_SKU", exception.getMessage(), request);
    }

    @ExceptionHandler(DuplicateVariantBarcodeException.class)
    ResponseEntity<ApiErrorResponse> duplicateVariantBarcode(
            DuplicateVariantBarcodeException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DUPLICATE_BARCODE", exception.getMessage(), request);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ApiErrorResponse> categoryNotFound(
            CategoryNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", exception.getMessage(), request);
    }

    @ExceptionHandler(StaleProductVersionException.class)
    ResponseEntity<ApiErrorResponse> staleVersion(
            StaleProductVersionException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "STALE_PRODUCT_VERSION", exception.getMessage(), request);
    }

    // Domain failures are exposed by domain-owned error codes, not by framework exception names.
    @ExceptionHandler(CatalogDomainException.class)
    ResponseEntity<ApiErrorResponse> domainFailure(
            CatalogDomainException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ApiErrorResponse> invalidRequest(
            IllegalArgumentException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_REQUEST", exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> invalidBody(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ADMIN_REQUEST",
                "Admin request validation failed",
                request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ApiErrorResponse> missingHeader(
            MissingRequestHeaderException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ADMIN_REQUEST",
                "Required admin request header is missing: " + exception.getHeaderName(),
                request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> malformedBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ADMIN_REQUEST",
                "Admin request body is malformed",
                request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiErrorResponse> invalidArgumentType(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "INVALID_ADMIN_REQUEST",
                "Admin request contains an invalid value for " + exception.getName(),
                request);
    }

    // Echo the trace id so operators can connect a failed admin request to logs/traces later.
    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request) {
        return ResponseEntity.status(status)
                .header("X-Trace-Id", request.getHeader("X-Trace-Id"))
                .body(ApiErrorResponse.of(code, message));
    }
}
