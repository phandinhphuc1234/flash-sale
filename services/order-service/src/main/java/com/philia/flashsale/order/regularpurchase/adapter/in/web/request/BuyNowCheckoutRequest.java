package com.philia.flashsale.order.regularpurchase.adapter.in.web.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.util.UUID;

/** Browser-submitted Buy Now price confirmation; the authenticated subject is never in this body. */
public record BuyNowCheckoutRequest(
        @NotNull UUID variantId,
        @Min(1) @Max(10) long quantity,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
                BigDecimal expectedUnitPrice,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) {
}
