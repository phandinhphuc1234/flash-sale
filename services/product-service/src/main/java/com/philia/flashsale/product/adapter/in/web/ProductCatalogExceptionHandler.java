package com.philia.flashsale.product.adapter.in.web;

import com.philia.flashsale.product.application.service.CategoryNotFoundException;
import com.philia.flashsale.product.application.service.InvalidCatalogRequestException;
import com.philia.flashsale.product.application.service.ProductNotFoundException;
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
    ResponseEntity<CatalogErrorResponse> handleCategoryNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new CatalogErrorResponse("CATEGORY_NOT_FOUND", "Category was not found"));
    }

    @ExceptionHandler(ProductNotFoundException.class)
    ResponseEntity<CatalogErrorResponse> handleProductNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new CatalogErrorResponse("PRODUCT_NOT_FOUND", "Product was not found"));
    }

    @ExceptionHandler({
            InvalidCatalogRequestException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<CatalogErrorResponse> handleInvalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(new CatalogErrorResponse("INVALID_CATALOG_REQUEST", exception.getMessage()));
    }
}
