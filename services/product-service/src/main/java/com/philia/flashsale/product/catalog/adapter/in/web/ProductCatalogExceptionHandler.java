package com.philia.flashsale.product.catalog.adapter.in.web;

import com.philia.flashsale.product.catalog.application.service.CategoryNotFoundException;
import com.philia.flashsale.product.catalog.application.service.InvalidCatalogRequestException;
import com.philia.flashsale.product.catalog.application.service.ProductNotFoundException;
import com.philia.flashsale.common.web.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = ProductCatalogController.class)
class ProductCatalogExceptionHandler {

    // Keep public catalog errors simple and stable; internal exception details stay inside the service.
    @ExceptionHandler(CategoryNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleCategoryNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("CATEGORY_NOT_FOUND", "Category was not found"));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleProductNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of("PRODUCT_NOT_FOUND", "Product was not found"));
    }

    @ExceptionHandler({
            InvalidCatalogRequestException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<ApiErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of("INVALID_CATALOG_REQUEST", "Catalog request is invalid"));
    }
}
