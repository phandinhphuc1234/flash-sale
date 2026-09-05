package com.philia.flashsale.order.regularpurchase.adapter.in.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Browser-submitted immutable Cart revision and price confirmations. */
public record CartCheckoutRequest(
        @Min(0) long cartVersion,
        @NotEmpty @Size(max = 20) List<@Valid CartCheckoutLineRequest> items) {

    public record CartCheckoutLineRequest(
            @NotNull UUID variantId,
            @Min(1) @Max(10) long quantity,
            @Min(1) long itemVersion,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
                    BigDecimal expectedUnitPrice,
            @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency) { }
}
