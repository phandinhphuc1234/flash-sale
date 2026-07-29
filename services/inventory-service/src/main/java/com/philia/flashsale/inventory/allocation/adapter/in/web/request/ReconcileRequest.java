package com.philia.flashsale.inventory.allocation.adapter.in.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** HTTP request used to settle sold and returned quantities for an allocation. */
public record ReconcileRequest(
        @NotNull Long soldQuantity,
        @NotNull Long returnedQuantity,
        @NotBlank String reason
) {
}
