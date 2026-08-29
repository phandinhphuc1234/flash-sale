package com.philia.flashsale.cart.adapter.in.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Public request for absolute replacement of one Cart item quantity. */
public record SetCartItemRequest(
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = 10, message = "quantity must be at most 10")
        Integer quantity) { }
